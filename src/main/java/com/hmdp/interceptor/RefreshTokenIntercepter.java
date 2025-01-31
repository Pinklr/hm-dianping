package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RefreshTokenIntercepter extends LoginInterceptor {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        Object user = request.getSession().getAttribute("user");
        log.info("进入拦截器，进行第一次拦截");
        //获取请求头中的token
        String authorization = request.getHeader("authorization");
        if(StringUtils.isEmpty(authorization)) {
            return true;
        }
        // 跟token获取用户的map
        Map<Object, Object> objectMap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + authorization);
        if(objectMap.isEmpty()) {

            return true;
        }
        //将map转为对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(objectMap, new UserDTO(), false);


        //保存对象信息
        //刷新对应的token有效期
//        if (user == null) {
//            response.setStatus(401);
//            // 未登录则跳转到登陆页面
////            request.getRequestDispatcher("/login").forward(request, response);
//            return false;
//        }
//        UserDTO userDTO = new UserDTO();
//
//        BeanUtils.copyProperties(user, userDTO);
        UserHolder.saveUser(userDTO);
        log.info("刷新有效期");
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + authorization, 30, TimeUnit.MINUTES);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}

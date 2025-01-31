package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // TODO 实现登录功能
        // 1 校验手机号
        if(loginForm.getPhone() == null || RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            log.info("手机号格式不正确：{}", loginForm.getPhone());
            return Result.fail("手机号格式不正确");
        }
//        // 2 check code
//        if(loginForm.getCode() == null || session.getAttribute("code") == null ||
//                !loginForm.getCode().equals(session.getAttribute("code"))) {
//            log.info("验证码不正确：{}", loginForm.getCode());
//            return Result.fail("验证码不正确");
//        }
        // 从redis中获取验证码
        String code = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + loginForm.getPhone());
        if(code == null || !loginForm.getCode().equals(code)) {
            log.info("验证码不正确：{}", loginForm.getCode());
            return Result.fail("验证码不正确");
        }
        // 3 query user from database
        String phone = loginForm.getPhone();
        User user = query().eq("phone", phone).one();
        // 4 create new user if not exist
        if(user == null) {
            user =  createUserWithPhone(phone);
        }
//        // 5 save user to session
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        // 将用户信息保存到redis中 并返回token
        String token = UUID.randomUUID().toString();
        // 将user转换为hash来存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> stringObjectMap = BeanUtil.beanToMap(userDTO);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));

        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, 30, TimeUnit.MINUTES);
        log.info("用户登录成功：{}", user);
        // 6 return
        return Result.ok(token);
    }

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1 校验手机号格式
        if(phone == null || RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式不正确!!!!");
        }
        //2 生成验证码
        String code = RandomUtil.randomNumbers(6);
//        //3 保存验证码到session
//        session.setAttribute("code", code);
        // 3 保存到redis中
        stringRedisTemplate.opsForValue( ).set(LOGIN_CODE_KEY + phone, code, 2, TimeUnit.MINUTES);
        //4 发送验证码
        log.info("验证码：{}", code);
        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        User user = User.builder()
                .phone(phone)
                .nickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(11))
                .build();
        save(user);
        return user;
    }
}

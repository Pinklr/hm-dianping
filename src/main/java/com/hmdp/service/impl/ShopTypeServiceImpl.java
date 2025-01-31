package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeList() {
        String shopTypeJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_TYPE_KEY);

        if(StrUtil.isNotBlank(shopTypeJson)) {
            JSONArray objects = JSONUtil.parseArray(shopTypeJson);
            List<ShopType> shopTypeList = JSONUtil.toList(objects, ShopType.class);
            log.info("店铺类型数据从Redis中获取");
            return Result.ok(shopTypeList);
        }
        log.info("从数据库查询店铺类型");
        List<ShopType> typeList = query().orderByAsc("sort").list();
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_TYPE_KEY, JSONUtil.toJsonStr(typeList));
        log.info("店铺类型数据缓存到Redis");
        return Result.ok(typeList);
    }
}

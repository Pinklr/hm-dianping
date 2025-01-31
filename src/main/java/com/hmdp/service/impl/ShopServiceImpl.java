package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryById(Long id) {
        Shop shop = queryWithLogicExpire(id);
        if(shop != null) {
            return Result.ok(shop);
        }else return Result.fail("店铺不存在");

    }

    private static final ExecutorService executorService = Executors.newFixedThreadPool(10);

    // 使用逻辑过期来解决缓存击穿的问题 存在缓存穿透的问题
    public Shop queryWithLogicExpire(Long id) {
        String key = "cache:shop:" + id;
        //从 redis 获取店铺信息
        String shopJosn = stringRedisTemplate.opsForValue().get(key);
        // if exists return directly
        if (StrUtil.isBlank(shopJosn)) {
            return null;
        }

        RedisData redisData = JSONUtil.toBean(shopJosn, RedisData.class);
        JSONObject jsonObject = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(jsonObject, Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 判断逻辑是否过期
//        过期了尝试获取互斥锁进行重建
        if(! LocalDateTime.now().isAfter(expireTime)) {
            // 开启新的线程
            return shop;
        }
        boolean isLock = tryLock(RedisConstants.LOCK_SHOP_KEY + id.toString());
//        没过期返回店铺的数据
        if(isLock) {
            executorService.submit(() -> {
                log.info("重建缓存");
                try {
                    this.setShop2Reids(id, 30);
                }finally {
                    unlock(RedisConstants.LOCK_SHOP_KEY + id.toString());
                }



            });
        }

        return shop;
//

//        获取到了锁 开启新的线程实现查询数据库的操作 主线程直接返回

//        没有获取到锁直接返回

    }

    //使用互斥锁解决缓存击穿的问题 使用存入空值解决缓存穿透的问题
    public Shop queryWithMutexLock(Long id) {
        String key = "cache:shop:" + id;
        //从 redis 获取店铺信息
        String shopJosn = stringRedisTemplate.opsForValue().get(key);
        // if exists return directly
        if (StrUtil.isNotBlank(shopJosn)) {
            Shop shop = JSONUtil.toBean(shopJosn, Shop.class);
            return shop;
        }
        if(shopJosn != null) {
            return null;
        }
        // if not exists query from db
        //获取互斥锁
        Shop shop =null;
        try {
            if(!tryLock(RedisConstants.LOCK_SHOP_KEY + id.toString() ) ) {
                Thread.sleep(100);
                return queryWithMutexLock(id);
            }

            shop = getById(id);
            // set to redis
            if(shop == null)  {
                //如果数据库不存在 缓存空数据
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        }catch (InterruptedException e) {
            e.printStackTrace();
        }finally {
            //释放互斥锁
            unlock(RedisConstants.LOCK_SHOP_KEY + id.toString());
        }





        //return
        return shop;
    }

    public Shop queryWithPassThrough(Long id) {
        String key = "cache:shop:" + id;
        //从 redis 获取店铺信息
        String shopJosn = stringRedisTemplate.opsForValue().get(key);
        // if exists return directly
        if (StrUtil.isNotBlank(shopJosn)) {
            Shop shop = JSONUtil.toBean(shopJosn, Shop.class);
            return shop;
        }
        if(shopJosn != null) {
            return null;
        }
        // if not exists query from db
        Shop shop = getById(id);
        // set to redis
        if(shop == null)  {
            //如果数据库不存在 缓存空数据
            stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //return
        return shop;

    }

    private boolean tryLock(String key) {
        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(aBoolean);
    }

    public void setShop2Reids(Long id, int expire) {
        Shop shop = getById(id);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusMinutes(expire));
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }





    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        if(shop.getId() == null) {
            return Result.fail("店铺信息不能为空");
        }
        // update db
        updateById(shop);
        // delete info from redis
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }
}

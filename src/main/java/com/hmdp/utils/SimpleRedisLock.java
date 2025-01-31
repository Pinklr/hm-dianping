package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    private String name;

    private StringRedisTemplate stringRedisTemplate;

    private String ID_String = UUID.randomUUID().toString(true).toString() + "-";
    private static final String PRIFIX_STRING = "lock:";

    private static final DefaultRedisScript<Long> UNLOCK_LUA;
    static {
        UNLOCK_LUA = new DefaultRedisScript<>();
        UNLOCK_LUA.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_LUA.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        long id = Thread.currentThread().getId();
        String value = ID_String + id;
        Boolean sucesss = stringRedisTemplate.opsForValue().setIfAbsent(PRIFIX_STRING + name,
                value, timeoutSec, TimeUnit.SECONDS);

        return Boolean.TRUE.equals(sucesss);
    }

    @Override
    public void unlock() {
//        String key = PRIFIX_STRING + name;
//        String value = ID_String + Thread.currentThread().getId();
//        String valueCurrent = stringRedisTemplate.opsForValue().get(key);
//        if(value.equals(valueCurrent)) {
//            stringRedisTemplate.delete(PRIFIX_STRING + name);
//        }
        stringRedisTemplate.execute(UNLOCK_LUA, Collections.singletonList(PRIFIX_STRING + name),
                ID_String + Thread.currentThread().getId());


    }
}

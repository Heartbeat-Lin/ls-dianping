package com.hmdp.utils.lock;


import lombok.NoArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@NoArgsConstructor
public class SimpleRedisLock implements ILock{

    private static final String KEY_PREFIX="lock:";

    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }


    @Override
    public boolean tryLock(String name,long timeSeconds) {
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name,
                Thread.currentThread().getId() + "", timeSeconds, TimeUnit.SECONDS);
        return success;
    }

//    @Override
//    public void unlock(String name) {
//        //1.删除锁，先判断是不是自己
//        String threadId = Thread.currentThread().getId()+"";
//        if (Objects.equals(threadId,stringRedisTemplate.opsForValue().get(KEY_PREFIX+name))){
//            stringRedisTemplate.delete(KEY_PREFIX+name);
//        }
//    }

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static{
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }
    @Override
    public void unlock(String name) {

        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(name),Thread.currentThread().getId()+"");

    }
}

package com.hmdp;

import cn.hutool.json.JSONUtil;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.utils.RedisWorker;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


@RunWith(SpringRunner.class)
@SpringBootTest
public class HmDianPingApplicationTests {


    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private static final ExecutorService es = Executors.newFixedThreadPool(10);
    @Test
    public void test1() throws InterruptedException {

        System.out.println(JSONUtil.toJsonStr(new VoucherOrder()));
    }

}

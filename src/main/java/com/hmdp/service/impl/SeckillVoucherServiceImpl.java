package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hmdp.config.RedissonConfig;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.SeckillVoucherMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.UserHolder;
import com.hmdp.utils.lock.SimpleRedisLock;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.index.PathBasedRedisIndexDefinition;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sun.awt.AppContext;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * <p>
 * 秒杀优惠券表，与优惠券是一对一关系 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2022-01-04
 */
@Service
@Slf4j
public class SeckillVoucherServiceImpl extends ServiceImpl<SeckillVoucherMapper, SeckillVoucher> implements ISeckillVoucherService {

    @Resource
    private IVoucherOrderService voucherOrderService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private RedissonClient redissonClient;

    private final static String ORDER_ID_PREFIX = "order";

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("order.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Result saveOrder(Long voucherId) {
        //1.查到对象
        SeckillVoucher seckillVoucher = getById(voucherId);
        log.debug("seckillVoucher:"+ JSONUtil.toJsonStr(seckillVoucher));
        //2.比对时间
        LocalDateTime beginTime = seckillVoucher.getBeginTime();
        if (beginTime.isAfter(LocalDateTime.now())){
            return Result.fail("秒杀还未开始");
        }
        LocalDateTime endTime = seckillVoucher.getEndTime();
        if (endTime.isBefore(LocalDateTime.now())){
            return Result.fail("秒杀已经结束");
        }


        //3.比对库存
        if (seckillVoucher.getStock()<1){
            return Result.fail("库存不足");
        }

        //抽象下面的逻辑到函数中，方便加锁

        /**
         *
         * 1.通过调用intern方法拿到唯一的用户id String的对象地址，从而降低锁的粒度
         * 2.在非Transactional方法中调用Transactional方法可能会引起其失效，因为实际调用的是this，需要拿到其代理对象再执行
         *  todo：这种方式在集群环境下仍会出现问题，因为多台服务器会有多个jvm，需要引入分布式锁
         */
//        synchronized (UserHolder.getUser().getId().toString().intern()){
//            //获取代理对象（事务）
//            SeckillVoucherServiceImpl proxy = (SeckillVoucherServiceImpl) AopContext.currentProxy();
//            return proxy.createVoucherOrder(seckillVoucher);
//        }

        /**
         * 改为分布式锁方案：
         * 1.原生方案
         * 2.redission方案
         */
//        SimpleRedisLock simpleRedisLock = new SimpleRedisLock(stringRedisTemplate);
//        boolean locked = simpleRedisLock.tryLock(Thread.currentThread().getName()+"",30);
        //改用redission方案
        RLock lock = redissonClient.getLock("lock:order:" + UserHolder.getUser().getId());
        boolean locked = false;
        try {
            locked = lock.tryLock(10, 30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
//        while (!locked){
//            try {
//                Thread.sleep(50);
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//            locked = simpleRedisLock.tryLock(Thread.currentThread().getName()+"",30);
//        }

        if (!locked){
            return Result.fail("加锁失败");
        }
        try{
            //获取代理对象（事务）
            Object o = AopContext.currentProxy();
            SeckillVoucherServiceImpl proxy = (SeckillVoucherServiceImpl)o;
            return proxy.createVoucherOrder(seckillVoucher);
        }finally {
            //simpleRedisLock.unlock(Thread.currentThread().getName()+"");
            lock.unlock(); // redission解锁
        }


    }

    @Override
    public Result saveOrderLua(Long voucherId) {


        //1.获取用户
        Long userId = UserHolder.getUser().getId();
        Long orderId = RedisWorker.nextId(ORDER_ID_PREFIX);

        //2.执行lua脚本
        Long executeRes = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString(), orderId.toString()
        );

        int res = executeRes.intValue();

        //3.判断返回结果
        if (res!=0){
            return Result.fail(res==1? "库存不足":"不能重复下单");
        }

        //todo：4.消息队列异步下单


        return Result.ok(orderId);
    }

    @Transactional
    public Result createVoucherOrder(SeckillVoucher seckillVoucher){
        //4.增加逻辑：一人一单
        int count = voucherOrderService.count(Wrappers.<VoucherOrder>lambdaQuery()
                .eq(VoucherOrder::getVoucherId, seckillVoucher.getVoucherId())
                .eq(VoucherOrder::getUserId, UserHolder.getUser().getId()));
        log.debug("voucherId:{},userId:{},count:{}",seckillVoucher.getVoucherId(),UserHolder.getUser().getId(), count);
        if (count>0){
            log.debug("没有下单成功");
            return Result.fail("该用户已经下过单");
        }
        //5.更新库存
        SeckillVoucher resVO = seckillVoucher.setStock(seckillVoucher.getStock() - 1);
        //5.1.优化sql，避免超卖
        int update = baseMapper.update(resVO, Wrappers.<SeckillVoucher>lambdaQuery()
                .eq(SeckillVoucher::getVoucherId, seckillVoucher.getVoucherId()).gt(SeckillVoucher::getStock,0));
        if (update==0){
            return Result.fail("库存不足");
        }
        log.debug("seckillVoucher:"+ JSONUtil.toJsonStr(resVO));

        //6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //6.1.设置订单id
        voucherOrder.setId(RedisWorker.nextId(ORDER_ID_PREFIX));

        //6.2.设置用户id
        voucherOrder.setUserId(UserHolder.getUser().getId());

        //6.3.设置代金券id
        voucherOrder.setVoucherId(seckillVoucher.getVoucherId());

        //7.插入订单记录到表中
        voucherOrderService.save(voucherOrder);
        log.debug("voucherOrder:{}",JSONUtil.toJsonStr(voucherOrder));
        return Result.ok(voucherOrder.getId());
    }

    @Transactional
    public Result createVoucherOrder(VoucherOrder voucherOrder){
        //一人一单逻辑在redis中已经判断过


        //扣减库存
        boolean updated = update(new LambdaUpdateWrapper<SeckillVoucher>()
                .eq(SeckillVoucher::getVoucherId, voucherOrder.getVoucherId())
                .gt(SeckillVoucher::getStock, 0)
                .setSql("stock=stock-1"));


        //.插入订单记录到表中
        voucherOrderService.save(voucherOrder);
        log.debug("voucherOrder:{}",JSONUtil.toJsonStr(voucherOrder));
        return Result.ok(voucherOrder.getId());
    }

    //异步处理线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    String queueName="stream.orders";

    //在类初始化之后执行，因为当这个类初始化好了之后，随时都是有可能要执行的
    // 用于线程池处理的任务
// 当初始化完毕后，就会去从对列中去拿信息
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(() -> {
            //String queueName="stream.orders";
            while (true) {
                try {
                    //从消息队列中获取订单信息
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1")
                            , StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2))
                            , StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    //判断消息时候获取成功
                    if (list==null||list.isEmpty()){
                        //获取失败 没有消息 继续循环
                        continue;
                    }
                    //获取成功 解析消息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    //下单
                    handleVoucherOrder(voucherOrder);
                    //ack确认消息
                    stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
                } catch (Exception e) {
                    e.printStackTrace();
                    handlePendingList();
                }
            }
        });
    }

    private void handlePendingList() {
        String queueName="stream.orders";
        while (true){
            try {
                //从消息队列中获取订单信息
                List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1")
                        , StreamReadOptions.empty().count(1)
                        , StreamOffset.create(queueName, ReadOffset.from("0"))
                );
                //判断消息时候获取成功
                if (list==null||list.isEmpty()){
                    //获取失败 没有消息 继续循环
                    break;
                }
                //获取成功 解析消息
                MapRecord<String, Object, Object> record = list.get(0);
                Map<Object, Object> values = record.getValue();
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                //下单
                handleVoucherOrder(voucherOrder);
                //ack确认消息
                stringRedisTemplate.opsForStream().acknowledge(queueName,"g1",record.getId());
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    Thread.sleep(20);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
    }

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        //创建锁对象（兜底）
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        //获取锁
        boolean isLock = lock.tryLock();
        //判断是否获取锁成功
        if (!isLock) {
            //获取失败,返回错误或者重试
            throw new RuntimeException("发送未知错误");
        }
        try {
            createVoucherOrder(voucherOrder);
        } finally {
            //释放锁
            lock.unlock();
        }

    }


}

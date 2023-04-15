package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.User;
import com.hmdp.entity.Voucher;
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
import org.springframework.aop.framework.AopContext;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sun.awt.AppContext;

import javax.annotation.Resource;
import java.time.LocalDateTime;

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
         * 改为分布式锁方案
         */
        SimpleRedisLock simpleRedisLock = new SimpleRedisLock(stringRedisTemplate);
        boolean locked = simpleRedisLock.tryLock(Thread.currentThread().getName()+"",30);
        //
        while (!locked){
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            locked = simpleRedisLock.tryLock(Thread.currentThread().getName()+"",30);
        }

        try{
            //获取代理对象（事务）
            Object o = AopContext.currentProxy();
            SeckillVoucherServiceImpl proxy = (SeckillVoucherServiceImpl)o;
            return proxy.createVoucherOrder(seckillVoucher);
        }finally {
            simpleRedisLock.unlock(Thread.currentThread().getName()+"");
        }


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
        voucherOrder.setId(RedisWorker.nextId("order"));

        //6.2.设置用户id
        voucherOrder.setUserId(UserHolder.getUser().getId());

        //6.3.设置代金券id
        voucherOrder.setVoucherId(seckillVoucher.getVoucherId());

        //7.插入订单记录到表中
        voucherOrderService.save(voucherOrder);
        log.debug("voucherOrder:{}",JSONUtil.toJsonStr(voucherOrder));
        return Result.ok(voucherOrder.getId());
    }




}

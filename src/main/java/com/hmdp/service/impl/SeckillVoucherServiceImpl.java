package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

        //4.更新库存
        SeckillVoucher resVO = seckillVoucher.setStock(seckillVoucher.getStock() - 1);
        int update = baseMapper.update(resVO, Wrappers.<SeckillVoucher>lambdaQuery()
                .eq(SeckillVoucher::getVoucherId, voucherId));
        if (update==0){
            return Result.fail("库存不足");
        }
        log.debug("seckillVoucher:"+ JSONUtil.toJsonStr(resVO));

        //5.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //5.1.设置订单id
        voucherOrder.setId(RedisWorker.nextId("order"));

        //5.2.设置用户id
        voucherOrder.setUserId(UserHolder.getUser().getId());

        //5.3.设置代金券id
        voucherOrder.setVoucherId(voucherId);

        //6.插入订单记录到表中
        voucherOrderService.save(voucherOrder);
        log.debug("voucherOrder:{}",JSONUtil.toJsonStr(voucherOrder));
        return Result.ok(voucherOrder.getId());
    }
}

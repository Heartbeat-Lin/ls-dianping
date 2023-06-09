package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {


    //缓存查询商品id
    public Result queryById(Long id);

    //互斥锁查询，解决缓存击穿
    public Shop queryWithMutex(Long id);

    //更新方法
    public Result update(Shop shop);

}

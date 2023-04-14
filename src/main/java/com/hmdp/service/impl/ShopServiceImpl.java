package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {

        String key = CACHE_SHOP_KEY + id;

        //1.从redis中查询
        String jsonStr = stringRedisTemplate.opsForValue().get(key);

        //2.查到就直接返回
        if (!StringUtils.isEmpty(jsonStr)){
            Shop shop = JSONUtil.toBean(jsonStr, Shop.class);
            return Result.ok(shop);
        }

        //3.没查到就去查mysql
        Shop shop = baseMapper.selectById(id);

        //4.判断Mysql中有无这条，无则返回错误
        if (shop == null)return Result.fail("shop不存在");

        //5.商铺存在，写入缓存
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),30L, TimeUnit.MINUTES);

        return Result.ok("shop为:"+shop);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {

        Long id = shop.getId();
        if (id==null)return Result.fail("店铺id不能为空");

        // 1.更新数据库
        updateById(shop);

        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+id);
        return Result.ok();
    }


}

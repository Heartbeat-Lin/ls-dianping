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

import static com.hmdp.utils.RedisConstants.*;

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


    //普通查询，可能会缓存击穿
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
    public Shop queryWithMutex(Long id)  {

        String key = CACHE_SHOP_KEY + id;

        //1.先查缓存
        String jsonStr = stringRedisTemplate.opsForValue().get(key);

        //2.查到就直接返回
        if (!StringUtils.isEmpty(jsonStr)){
            return JSONUtil.toBean(jsonStr, Shop.class);
        }

        //3.没查到就加锁
        boolean lockGot = tryLock(key);
        Shop shop = null;
        //4.得到shop实体
        try {
            if (lockGot){
                shop = baseMapper.selectById(id);

            } else {
                Thread.sleep(50);
                shop = queryWithMutex(id);
            }
            //5.shop判空
            if (shop == null){
                //5.1.缓存空值
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }

            //6.存入值
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL,TimeUnit.MINUTES);

        }catch (Exception e){
            e.printStackTrace();
        }finally {
            //7.释放锁
            unlock(key);
        }

        return shop;
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


    private boolean tryLock(String key){

        return stringRedisTemplate.opsForValue().setIfAbsent(key,"1",10,TimeUnit.SECONDS);
    }

    private void unlock(String key){
        stringRedisTemplate.delete(key);
    }

}

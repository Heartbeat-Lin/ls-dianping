package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    //获取是否关注
    public Result isFollow(Long userId);

    //关注/取消关注
    public Result followOrNot(Long userId, Boolean isFollow);

    Result getCommonFollow(Long userId);

}

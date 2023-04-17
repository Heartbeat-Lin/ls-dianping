package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IUserService userService;

    @Override
    public Result isFollow(Long userId) {
        //1.获取当前用户
        UserDTO user = UserHolder.getUser();
        Long userIdCurrent = user.getId();
        //2.查询记录数
        Integer count = baseMapper.selectCount(Wrappers.<Follow>lambdaQuery()
                .eq(Follow::getUserId, userIdCurrent)
                .eq(Follow::getFollowUserId, userId));
        if (null == count || count==0)return Result.ok(false);

        return Result.ok(true);
    }

    @Override
    public Result followOrNot(Long userId, Boolean isFollow) {
        //1.拿到当前用户
        UserDTO user = UserHolder.getUser();

        String setKey = "follows:"+user.getId();

        //2.判断关注or取关
        if (isFollow){
            Follow follow = new Follow();
            follow.setUserId(user.getId());
            follow.setFollowUserId(userId);
            boolean success = save(follow);
            if (!success)return Result.fail("保存失败");
            stringRedisTemplate.opsForSet().add(setKey,userId.toString());
        }else {
            boolean remove = remove(Wrappers.<Follow>lambdaQuery()
                    .eq(Follow::getUserId, user.getId())
                    .eq(Follow::getFollowUserId, userId));
            if (!remove)return Result.fail("取消关注失败");
            stringRedisTemplate.opsForSet().remove(setKey,userId.toString());
        }
        return Result.ok("操作成功");
    }

    @Override
    public Result getCommonFollow(Long userId) {
        //1.获取当前用户
        UserDTO user = UserHolder.getUser();

        String currentUserKey = "follows:"+ user.getId();

        String targetUserKey = "follows:"+userId;

        //2.得到共同关注id
        Set<String> set = stringRedisTemplate.opsForSet().intersect(currentUserKey, targetUserKey);

        if (set == null || set.isEmpty())return Result.ok(Collections.emptyList());

        List<Long> idList = set.stream().map(Long::valueOf).collect(Collectors.toList());

        List<User> users = userService.listByIds(idList);
        List<UserDTO> userDTOS =
                users.stream().map(user1 -> BeanUtil.copyProperties(user1, UserDTO.class)).collect(Collectors.toList());
        log.debug("userDTO:{}",userDTOS);

        return Result.ok(userDTOS);
    }


}

package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
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
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private UserServiceImpl userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result showBlog(Long blogId) {
        //1.查出blog
        Blog blog = getById(blogId);
        if (blog == null){
            return Result.fail("未找到相关博文");
        }

        // 2.查询blog有关的用户，给blog赋值
        queryBlogUser(blog);
        log.debug("进入showBlog判断逻辑");

        return Result.ok(blog);
    }

    //改为zset方式
    @Override
    public Result likeBlog(Long blogId) {
        String va_12;

        //1.查出相关user
        UserDTO user = UserHolder.getUser();


        String key = RedisConstants.BLOG_LIKED_KEY + blogId;
        //2.判断有没有点赞过
        Double score = stringRedisTemplate.opsForZSet().score(key, user.getId().toString());

        //3.如果没有，增加点赞数，更新redis和数据库
        if (score == null){
            stringRedisTemplate.opsForZSet().add(key,user.getId().toString(),System.currentTimeMillis());
            update(new LambdaUpdateWrapper<Blog>()
                    .eq(Blog::getId,blogId)
                    .setSql("liked=liked+1"));
        }
        //4.如果有，取消点赞
        else {
            stringRedisTemplate.opsForZSet().remove(key,user.getId().toString());
            update(new LambdaUpdateWrapper<Blog>()
                    .eq(Blog::getId,blogId)
                    .setSql("liked=liked-1"));
        }

        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long blogId) {
        String key = RedisConstants.BLOG_LIKED_KEY + blogId;

        //1.查出top5
        Set<String> userIdSet = stringRedisTemplate.opsForZSet().range(key, 0, 4);

        //2.判空
        if (null == userIdSet || userIdSet.isEmpty()){
            return Result.ok(Collections.emptyList());
        }

        List<Long> userIdList = userIdSet.stream().map(Long::valueOf).collect(Collectors.toList());
        String sqlStr = StrUtil.join(",", userIdList);
        //3.查出实体
        List<User> userList = userService.list(Wrappers.<User>lambdaQuery()
                .in(User::getId, userIdList)
                        .last("ORDER BY FIELD(id," + sqlStr + ")")
                );
        log.debug("userList:{}",JSONUtil.toJsonStr(userList.stream().map(User::getId).collect(Collectors.toList())));

        List<UserDTO> userDTOList =
                userList.stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());


        return Result.ok(userDTOList);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
    }




}

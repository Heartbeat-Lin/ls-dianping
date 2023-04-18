package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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
    private IFollowService followService;

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
        isBlogLiked(blog);
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

    @Override
    public Result saveBlog(Blog blog) {
        //1.获取用户，设置blog的用户id
        UserDTO user = UserHolder.getUser();

        blog.setUserId(user.getId());
        //2.保存blog
        save(blog);

        //3.查出粉丝:FollowList
        List<Follow> followList = followService.list(Wrappers.<Follow>lambdaQuery()
                .eq(Follow::getFollowUserId, user.getId().toString()));
        if (null==followList || followList.isEmpty())return Result.ok(blog.getId());

        //4.将followList转换成userIdList
        List<Long> userList = followList.stream().map(Follow::getUserId).collect(Collectors.toList());


        //5.保存到redis中，发到关注用户的邮箱
        for (Long userId : userList) {
            String key = RedisConstants.FEED_KEY + userId.toString();
            stringRedisTemplate.opsForZSet().add(key,user.getId().toString(),System.currentTimeMillis());
        }
        log.debug("进入了saveBlog保存逻辑");
        return Result.ok(blog.getId());
    }


    /**
     *
     * @param maxTime: 上一次查询的最大时间
     * @param offset:避免重复查询的偏移量
     * @return
     */
    @Override
    public Result queryBlogOfFollow(Long maxTime, Integer offset) {
        //1.获取当前用户
        Long curUserId = UserHolder.getUser().getId();

        String key = RedisConstants.FEED_KEY + curUserId;
        log.debug("queryBlogOfFollow,userId:{}",curUserId);

        //2.查询收件箱
        Set<ZSetOperations.TypedTuple<String>> typedTuples =
                stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, maxTime, offset, 2);


        //3.判断是否为空，空则直接返回
        if (null == typedTuples || typedTuples.isEmpty()){
            return Result.ok();
        }

        //4.查出userIdList
        List<Long> userIdList =
                typedTuples.stream().map(ZSetOperations.TypedTuple::getValue).map(Long::valueOf).collect(Collectors.toList());
        log.debug("userIdList:{}",userIdList);

        //5.解析出下一次的offset
        long minTime = 0;//下一次的最小时间
        Integer os = 1; //本次的偏移量，加上上次的偏移量则为总的偏移量
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            long time = tuple.getScore().longValue();

            if (time == minTime){
                os++;
            }else {
                minTime = time;
                os = 1;
            }
        }

        os = minTime==maxTime ? os:os+offset;


        //6.根据id查询Blog
        String sqlStr = StrUtil.join(",", userIdList);
        List<Blog> blogs = baseMapper.selectList(Wrappers.<Blog>lambdaQuery()
                .in(Blog::getUserId, userIdList)
                .last("order by field(id," + sqlStr + ")"));

        log.debug("BlogService,blogs:{}",blogs);


        //7.填充blogs信息
        for (Blog blog : blogs) {
            queryBlogUser(blog);
            isBlogLiked(blog);
        }

        //8.填充实体并返回
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
    }


    //查询blog的用户并设置值进去
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
    }


    //查看blog是否被（当前用户）点赞
    private void isBlogLiked(Blog blog){
        String  key = RedisConstants.BLOG_LIKED_KEY + blog.getId();

        Double score = stringRedisTemplate.opsForZSet().score(key, UserHolder.getUser().getId().toString());

        blog.setIsLike(score!=null);

    }




}

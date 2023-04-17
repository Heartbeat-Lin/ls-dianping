package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    public Result showBlog(Long blogId);

    //点赞博文
    public Result likeBlog(Long blogId);

    //查询喜欢列表
    public Result queryBlogLikes(Long blogId);

}

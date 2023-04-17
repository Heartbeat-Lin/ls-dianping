package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    private IFollowService followService;

    @GetMapping("/or/not/{userId}")
    public Result isFollow(@PathVariable("userId") Long userId){
        Result res = followService.isFollow(userId);
        return res;
    }

    @PutMapping("/{userId}/{isFollow}")
    public Result followOrNot(@PathVariable("userId")Long userId,@PathVariable("isFollow")Boolean isFollow){
        Result result = followService.followOrNot(userId,isFollow);
        return result;
    }

    @GetMapping("/common/{userId}")
    public Result getCommonFollow(@PathVariable("userId")Long userId){
        return followService.getCommonFollow(userId);
    }

}

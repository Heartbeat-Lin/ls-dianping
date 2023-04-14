package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.Objects;

import static com.baomidou.mybatisplus.core.toolkit.Wrappers.query;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.如果不符合，返回错误信息
            return Result.fail("手机号格式错误！");
        }
        // 3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        // 4.保存验证码到 session
        session.setAttribute("code",code);
        // 5.发送验证码
        log.debug("发送短信验证码成功，验证码：{}", code);
        // 返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        //1.校验手机号格式
        if (!RegexUtils.isPhoneInvalid(phone)){
            Result.fail("手机号格式错误");
        }

        //2.校验验证码
        Object codeObj = session.getAttribute("code");
        String loginFormCode = loginForm.getCode();
        if (codeObj==null || loginFormCode==null || !Objects.equals(loginForm.getCode(),codeObj.toString())){
            return Result.fail("验证码校验失败");
        }


        //3.查询用户是否存在
        User user = query().eq("phone", phone).one();
        if (null==user){
            user = createUserWithPhone(phone);
        }

        //4.保存用户
        session.setAttribute("user",user);


        return Result.ok("功能未完成");
    }


    //用手机号创建用户
    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(RandomUtil.randomString(10));

        return user;
    }
}

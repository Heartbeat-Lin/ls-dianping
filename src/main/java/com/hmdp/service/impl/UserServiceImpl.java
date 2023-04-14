package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static com.baomidou.mybatisplus.core.toolkit.Wrappers.query;
import static com.hmdp.utils.RedisConstants.*;

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

    @Resource
    private StringRedisTemplate stringRedisTemplate;

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
        //session.setAttribute("code",code);

        // 4.保存用户信息到redis
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY+phone,code);

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

        //2.校验验证码,改为redis
        String code = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        String loginFormCode = loginForm.getCode();
        if (code==null || loginFormCode==null || !Objects.equals(loginFormCode,code)){
            return Result.fail("验证码校验失败");
        }
        //3.查询用户是否存在
        User user = query().eq("phone", phone).one();
        if (null==user){
            user = createUserWithPhone(phone);
        }

        //4.保存用户
        //session.setAttribute("user",user);

        //4.改进：保存用户信息到redis
        String token = UUID.randomUUID().toString() ;
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        //4.1.转bean为map
        Map<String, Object> userDTOMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        //5.存到redis hash对象中
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY+token,userDTOMap);
        //6.设置token过期时间
        stringRedisTemplate.expire(LOGIN_USER_KEY+token,LOGIN_USER_TTL, TimeUnit.MINUTES);
//login:token:a4359cfa-8ffc-437d-9562-4a5d26e74578


        return Result.ok(LOGIN_USER_KEY+token);
    }


    //用手机号创建用户
    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(RandomUtil.randomString(10));

        //添加进数据库
        baseMapper.insert(user);
        //添加进缓存
        log.debug("添加进缓存"+ JSONUtil.toJsonStr(user));
        UserHolder.saveUser(BeanUtil.copyProperties(user,UserDTO.class));
        log.debug("获得内容"+JSONUtil.toJsonStr(UserHolder.getUser()));

        return user;
    }
}

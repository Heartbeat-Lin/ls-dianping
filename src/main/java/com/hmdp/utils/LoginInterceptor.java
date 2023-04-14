package com.hmdp.utils;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@Slf4j
public class LoginInterceptor implements HandlerInterceptor {




    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        //1.获取session
        HttpSession session = request.getSession();

        //2.获取对象
        User user = (User) session.getAttribute("user");

        //3.判断对象是否为空
        if (user==null){
            //返回401状态码
            response.setStatus(401);
            return false;
        }

        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user,userDTO);
        log.debug("userDTO:{}",JSONUtil.toJsonStr(userDTO));

        //4.存在就保存到ThreadLocal，并返回true
        UserHolder.saveUser(userDTO);

        return true;
    }

    public static UserDTO castUserToDTO(User user){
        UserDTO userDTO = new UserDTO();
        userDTO.setId(user.getId());
        userDTO.setNickName(user.getNickName());
        if (user.getIcon()!=null)userDTO.setIcon(user.getIcon());
        return userDTO;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户
        UserHolder.removeUser();
    }
}

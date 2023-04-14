package com.hmdp.utils;

import com.hmdp.entity.User;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

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

        //4.存在就保存到ThreadLocal，并返回true


        return false;
    }
}

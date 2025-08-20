package com.hmdp.interceptor;

import com.hmdp.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.判断threadlocal中是否保存有用户信息
        if (UserHolder.getUser() == null) {
            // 没有则需要拦截
            response.setStatus(401); // 设置状态码
            return false; // 拦截
        }

        // 2.放行
        return true;
    }
}

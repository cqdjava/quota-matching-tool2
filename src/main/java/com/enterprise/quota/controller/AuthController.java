package com.enterprise.quota.controller;

import com.enterprise.quota.entity.User;
import com.enterprise.quota.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    @Autowired
    private UserService userService;
    
    /**
     * 用户登录
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestParam String username,
            @RequestParam String password,
            HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        
        User user = userService.validateLogin(username, password);
        if (user != null) {
            // 将用户信息存入Session
            session.setAttribute("userId", user.getId());
            session.setAttribute("username", user.getUsername());
            session.setAttribute("realName", user.getRealName());
            
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("username", user.getUsername());
            userInfo.put("realName", user.getRealName() != null ? user.getRealName() : user.getUsername());
            
            result.put("success", true);
            result.put("message", "登录成功");
            result.put("user", userInfo);
            return ResponseEntity.ok(result);
        } else {
            result.put("success", false);
            result.put("message", "用户名或密码错误");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }
    }
    
    /**
     * 用户登出
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpSession session) {
        session.invalidate();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "登出成功");
        return ResponseEntity.ok(result);
    }
    
    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> getCurrentUser(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        Long userId = (Long) session.getAttribute("userId");
        
        if (userId != null) {
            return userService.getUserById(userId).map(user -> {
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("id", user.getId());
                userInfo.put("username", user.getUsername());
                userInfo.put("realName", user.getRealName() != null ? user.getRealName() : user.getUsername());
                userInfo.put("role", user.getRole() != null ? user.getRole() : "user"); // 添加角色信息
                
                result.put("success", true);
                result.put("user", userInfo);
                return ResponseEntity.ok(result);
            }).orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result));
        } else {
            result.put("success", false);
            result.put("message", "未登录");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
        }
    }
    
    /**
     * 检查登录状态
     */
    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> checkLogin(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        Long userId = (Long) session.getAttribute("userId");
        
        if (userId != null) {
            result.put("success", true);
            result.put("loggedIn", true);
            result.put("username", session.getAttribute("username"));
        } else {
            result.put("success", true);
            result.put("loggedIn", false);
        }
        return ResponseEntity.ok(result);
    }
    
    /**
     * 用户注册
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam(required = false) String realName,
            @RequestParam(required = false) String email) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (username == null || username.trim().isEmpty()) {
                result.put("success", false);
                result.put("message", "用户名不能为空");
                return ResponseEntity.badRequest().body(result);
            }
            if (password == null || password.trim().isEmpty()) {
                result.put("success", false);
                result.put("message", "密码不能为空");
                return ResponseEntity.badRequest().body(result);
            }
            if (password.length() < 6) {
                result.put("success", false);
                result.put("message", "密码长度不能少于6位");
                return ResponseEntity.badRequest().body(result);
            }
            
            userService.registerUser(username, password, realName, email);
            result.put("success", true);
            result.put("message", "注册成功，请登录");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "注册失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
}


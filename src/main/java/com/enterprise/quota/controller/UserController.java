package com.enterprise.quota.controller;

import com.enterprise.quota.entity.User;
import com.enterprise.quota.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user")
public class UserController {
    
    @Autowired
    private UserService userService;
    
    /**
     * 获取所有用户列表
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> getAllUsers() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<User> users = userService.getAllUsers();
            List<Map<String, Object>> userList = users.stream().map(user -> {
                Map<String, Object> userInfo = new HashMap<>();
                userInfo.put("id", user.getId());
                userInfo.put("username", user.getUsername());
                userInfo.put("realName", user.getRealName());
                userInfo.put("email", user.getEmail());
                userInfo.put("status", user.getStatus());
                userInfo.put("statusText", user.getStatus() == 1 ? "启用" : "禁用");
                userInfo.put("createTime", user.getCreateTime());
                userInfo.put("updateTime", user.getUpdateTime());
                return userInfo;
            }).collect(Collectors.toList());
            
            result.put("success", true);
            result.put("users", userList);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "获取用户列表失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 注册新用户
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerUser(
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
            
            User user = userService.registerUser(username, password, realName, email);
            result.put("success", true);
            result.put("message", "注册成功");
            result.put("userId", user.getId());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "注册失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 修改当前用户密码
     */
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(
            @RequestParam String oldPassword,
            @RequestParam String newPassword,
            HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long userId = (Long) session.getAttribute("userId");
            if (userId == null) {
                result.put("success", false);
                result.put("message", "请先登录");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
            }
            
            if (newPassword == null || newPassword.length() < 6) {
                result.put("success", false);
                result.put("message", "新密码长度不能少于6位");
                return ResponseEntity.badRequest().body(result);
            }
            
            boolean success = userService.changePassword(userId, oldPassword, newPassword);
            if (success) {
                result.put("success", true);
                result.put("message", "密码修改成功");
                return ResponseEntity.ok(result);
            } else {
                result.put("success", false);
                result.put("message", "原密码错误");
                return ResponseEntity.badRequest().body(result);
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "修改密码失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 更新用户信息
     */
    @PutMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> updateUser(
            @PathVariable Long userId,
            @RequestParam(required = false) String realName,
            @RequestParam(required = false) String email) {
        Map<String, Object> result = new HashMap<>();
        try {
            User user = userService.updateUser(userId, realName, email);
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("username", user.getUsername());
            userInfo.put("realName", user.getRealName() != null ? user.getRealName() : "");
            userInfo.put("email", user.getEmail() != null ? user.getEmail() : "");
            
            result.put("success", true);
            result.put("message", "更新成功");
            result.put("user", userInfo);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "更新失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 删除用户
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable Long userId, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long currentUserId = (Long) session.getAttribute("userId");
            if (currentUserId != null && currentUserId.equals(userId)) {
                result.put("success", false);
                result.put("message", "不能删除当前登录的用户");
                return ResponseEntity.badRequest().body(result);
            }
            
            userService.deleteUser(userId);
            result.put("success", true);
            result.put("message", "删除成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "删除失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 启用/禁用用户
     */
    @PutMapping("/{userId}/status")
    public ResponseEntity<Map<String, Object>> updateUserStatus(
            @PathVariable Long userId,
            @RequestParam Integer status) {
        Map<String, Object> result = new HashMap<>();
        try {
            userService.updateUserStatus(userId, status);
            result.put("success", true);
            result.put("message", status == 1 ? "用户已启用" : "用户已禁用");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "操作失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
    
    /**
     * 重置用户密码（管理员功能）
     */
    @PostMapping("/{userId}/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(
            @PathVariable Long userId,
            @RequestParam String newPassword) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (newPassword == null || newPassword.length() < 6) {
                result.put("success", false);
                result.put("message", "新密码长度不能少于6位");
                return ResponseEntity.badRequest().body(result);
            }
            
            userService.resetPassword(userId, newPassword);
            result.put("success", true);
            result.put("message", "密码重置成功");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "重置密码失败：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
}


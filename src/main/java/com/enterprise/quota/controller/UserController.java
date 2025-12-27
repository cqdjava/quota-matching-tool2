package com.enterprise.quota.controller;

import com.enterprise.quota.entity.User;
import com.enterprise.quota.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/user")
public class UserController {
    
    @Autowired
    private UserService userService;
    
    private boolean isAdmin(HttpSession session) {
        Long currentUserId = (Long) session.getAttribute("userId");
        if (currentUserId == null) {
            return false;
        }
        
        return userService.getUserById(currentUserId)
                .map(user -> "admin".equals(user.getRole()))
                .orElse(false);
    }
    
    /**
     * 获取用户列表（管理员可查看所有用户，普通用户只能查看自己）
     */
    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> getAllUsers(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long currentUserId = (Long) session.getAttribute("userId");
            if (currentUserId == null) {
                result.put("success", false);
                result.put("message", "请先登录");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
            }
            
            List<User> users;
            if (isAdmin(session)) {
                // 管理员可查看所有用户
                users = userService.getAllUsers();
            } else {
                // 普通用户只能查看自己
                User currentUser = userService.getUserById(currentUserId)
                    .orElseThrow(() -> new RuntimeException("用户不存在"));
                users = Arrays.asList(currentUser);
            }
            
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
                userInfo.put("role", user.getRole()); // 添加角色信息
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
     * 注册新用户（仅管理员可操作）
     */
    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerUser(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam(required = false) String realName,
            @RequestParam(required = false) String email,
            HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 权限检查：只有管理员可以创建新用户
            if (!isAdmin(session)) {
                result.put("success", false);
                result.put("message", "无权限创建用户");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
            }
            
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
     * 更新用户信息（管理员可更新任意用户，普通用户只能更新自己）
     */
    @PutMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> updateUser(
            @PathVariable Long userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String realName,
            @RequestParam(required = false) String email,
            HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long currentUserId = (Long) session.getAttribute("userId");
            if (currentUserId == null) {
                result.put("success", false);
                result.put("message", "请先登录");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
            }
            
            // 权限检查：管理员可以更新任意用户，普通用户只能更新自己
            if (!isAdmin(session) && !currentUserId.equals(userId)) {
                result.put("success", false);
                result.put("message", "无权限更新其他用户的信息");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
            }
            
            // 权限检查：只有管理员可以更新用户名
            if (username != null && !isAdmin(session)) {
                result.put("success", false);
                result.put("message", "无权限修改用户名");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
            }
            
            User user = userService.updateUser(userId, username, realName, email);
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", user.getId());
            userInfo.put("username", user.getUsername());
            userInfo.put("realName", user.getRealName() != null ? user.getRealName() : "");
            userInfo.put("email", user.getEmail() != null ? user.getEmail() : "");
            userInfo.put("role", user.getRole()); // 添加角色信息
            
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
     * 删除用户（仅管理员可删除用户）
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable Long userId, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        try {
            Long currentUserId = (Long) session.getAttribute("userId");
            if (currentUserId == null) {
                result.put("success", false);
                result.put("message", "请先登录");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
            }
            
            // 权限检查：只有管理员可以删除用户
            if (!isAdmin(session)) {
                result.put("success", false);
                result.put("message", "无权限删除用户");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
            }
            
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
     * 启用/禁用用户（仅管理员可操作）
     */
    @PutMapping("/{userId}/status")
    public ResponseEntity<Map<String, Object>> updateUserStatus(
            @PathVariable Long userId,
            @RequestParam Integer status,
            HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 权限检查：只有管理员可以操作
            if (!isAdmin(session)) {
                result.put("success", false);
                result.put("message", "无权限操作用户状态");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
            }
            
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
     * 重置用户密码（仅管理员可操作）
     */
    @PostMapping("/{userId}/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(
            @PathVariable Long userId,
            @RequestParam String newPassword,
            HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 权限检查：只有管理员可以重置用户密码
            if (!isAdmin(session)) {
                result.put("success", false);
                result.put("message", "无权限重置用户密码");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(result);
            }
            
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


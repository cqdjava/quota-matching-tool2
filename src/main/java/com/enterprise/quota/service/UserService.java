package com.enterprise.quota.service;

import com.enterprise.quota.entity.User;
import com.enterprise.quota.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;

@Service
public class UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    /**
     * 验证用户登录
     */
    public User validateLogin(String username, String password) {
        Optional<User> userOpt = userRepository.findByUsernameAndStatus(username, 1);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (verifyPassword(password, user.getPassword())) {
                return user;
            }
        }
        return null;
    }
    
    /**
     * 创建用户（密码会自动加密）
     */
    @Transactional
    public User createUser(String username, String password, String realName, String email) {
        // 检查用户名是否已存在
        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("用户名已存在");
        }
        
        User user = new User();
        user.setUsername(username);
        user.setPassword(encryptPassword(password));
        user.setRealName(realName);
        user.setEmail(email);
        user.setStatus(1);
        return userRepository.save(user);
    }
    
    /**
     * 注册新用户
     */
    @Transactional
    public User registerUser(String username, String password, String realName, String email) {
        return createUser(username, password, realName, email);
    }
    
    /**
     * 更新用户信息
     */
    @Transactional
    public User updateUser(Long userId, String username, String realName, String email) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (username != null && !username.trim().isEmpty()) {
                // 检查新用户名是否与其他用户重复
                Optional<User> existingUser = userRepository.findByUsername(username);
                if (existingUser.isPresent() && !existingUser.get().getId().equals(userId)) {
                    throw new RuntimeException("用户名已存在");
                }
                user.setUsername(username);
            }
            if (realName != null) {
                user.setRealName(realName);
            }
            if (email != null) {
                user.setEmail(email);
            }
            return userRepository.save(user);
        }
        throw new RuntimeException("用户不存在");
    }
    
    /**
     * 删除用户
     */
    @Transactional
    public void deleteUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new RuntimeException("用户不存在");
        }
        userRepository.deleteById(userId);
    }
    
    /**
     * 启用/禁用用户
     */
    @Transactional
    public void updateUserStatus(Long userId, Integer status) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setStatus(status);
            userRepository.save(user);
        } else {
            throw new RuntimeException("用户不存在");
        }
    }
    
    /**
     * 重置密码（管理员功能）
     */
    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setPassword(encryptPassword(newPassword));
            userRepository.save(user);
        } else {
            throw new RuntimeException("用户不存在");
        }
    }
    
    /**
     * 修改密码
     */
    @Transactional
    public boolean changePassword(Long userId, String oldPassword, String newPassword) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (verifyPassword(oldPassword, user.getPassword())) {
                user.setPassword(encryptPassword(newPassword));
                userRepository.save(user);
                return true;
            }
        }
        return false;
    }
    
    /**
     * 更新用户角色
     */
    @Transactional
    public void updateUserRoleByUsername(String username, String role) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setRole(role);
            userRepository.save(user);
        }
    }
    
    /**
     * 获取所有用户
     */
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
    
    /**
     * 根据ID获取用户
     */
    public Optional<User> getUserById(Long id) {
        return userRepository.findById(id);
    }
    
    /**
     * 加密密码（使用SHA-256）
     */
    private String encryptPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("密码加密失败", e);
        }
    }
    
    /**
     * 验证密码
     */
    private boolean verifyPassword(String inputPassword, String storedPassword) {
        String encryptedInput = encryptPassword(inputPassword);
        return encryptedInput.equals(storedPassword);
    }
}


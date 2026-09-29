package com.aichat.service;

import com.aichat.entity.SysUser;
import com.aichat.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    // 简易登录态：token -> 用户信息（重启失效，正式项目换JWT）
    private final Map<String, SysUser> tokenMap = new ConcurrentHashMap<>();

    @Autowired
    private SysUserMapper sysUserMapper;

    // 校验账号密码，成功返回token，失败返回null
    public String login(String username, String password) {
        if (username == null || password == null) {
            return null;
        }
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, username);
        SysUser user = sysUserMapper.selectOne(wrapper);
        // 密码与role比较忽略大小写，兼容库里既有的小写数据
        if (user == null || !user.getPassword().equals(password)) {
            return null;
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        tokenMap.put(token, user);
        return token;
    }

    // 根据token查登录用户，未登录返回null
    public SysUser resolve(String token) {
        if (token == null) {
            return null;
        }
        return tokenMap.get(token);
    }

    // 退出登录
    public void logout(String token) {
        if (token != null) {
            tokenMap.remove(token);
        }
    }

    // 判断用户是否为客服角色（AGENT/ADMIN，忽略大小写）
    public boolean isAgent(SysUser user) {
        if (user == null || user.getRole() == null) {
            return false;
        }
        return "AGENT".equalsIgnoreCase(user.getRole()) || "ADMIN".equalsIgnoreCase(user.getRole());
    }
}

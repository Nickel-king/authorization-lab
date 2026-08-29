package com.example.authz.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.authz.user.entity.User;
import com.example.authz.user.mapper.UserMapper;
import com.example.authz.user.service.UserService;
import org.springframework.stereotype.Service;

/**
 * 用户（User）服务默认实现。
 * <p>
 * 继承 MyBatis-Plus {@link ServiceImpl}，复用基础 CRUD 能力实现
 * {@link UserService}，无需额外业务逻辑。
 *
 * @author Nickel
 * @since 2026-08-28
 */
@Service
public class UserServiceImpl
        extends ServiceImpl<UserMapper, User>
        implements UserService {
}
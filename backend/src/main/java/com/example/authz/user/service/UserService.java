package com.example.authz.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.authz.user.entity.User;

/**
 * 用户（User）服务接口。
 * <p>
 * 继承 MyBatis-Plus {@link IService}，为基础的用户 CRUD 操作提供统一入口，
 * 供控制器与授权上下文构建等模块调用。
 *
 * @author Nickel
 * @since 2026-08-28
 */
public interface UserService extends IService<User> {
}
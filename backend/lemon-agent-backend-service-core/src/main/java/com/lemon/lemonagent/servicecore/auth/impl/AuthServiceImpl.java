package com.lemon.lemonagent.servicecore.auth.impl;

import com.lemon.lemonagent.dal.entity.UserAccountEntity;
import com.lemon.lemonagent.dal.repository.UserAccountRepository;
import com.lemon.lemonagent.model.auth.LoginData;
import com.lemon.lemonagent.model.auth.UserData;
import com.lemon.lemonagent.servicecore.auth.AuthService;
import com.lemon.lemonagent.servicecore.auth.TokenStore;
import com.lemon.lemonagent.util.crypto.PasswordHasher;
import com.lemon.lemonagent.util.crypto.PasswordPolicy;
import com.lemon.architecture.model.BusinessException;
import com.lemon.architecture.model.ResultCodes;
import org.springframework.stereotype.Service;

/**
 * 认证领域服务实现。
 *
 * <p>两个安全口径：① 登录失败统一报「账号或密码错误」，不区分"用户不存在"与"密码错误"，
 * 避免账号枚举；② 改密成功后吊销该用户当前令牌——虽然其他端口的旧会话仍在（v1 单令牌
 * 模型，见 TokenStore），当前持有者必须重新登录。</p>
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final UserAccountRepository userAccountRepository;

    private final TokenStore tokenStore;

    public AuthServiceImpl(UserAccountRepository userAccountRepository, TokenStore tokenStore) {
        this.userAccountRepository = userAccountRepository;
        this.tokenStore = tokenStore;
    }

    @Override
    public LoginData login(String username, String password) {
        if (isBlank(username) || isBlank(password)) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "用户名和密码不能为空");
        }
        UserAccountEntity user = userAccountRepository.findByUsername(username.trim());
        // 用户不存在与口令错误走同一文案、同一码，不做账号枚举
        if (user == null || !PasswordHasher.verify(password, user.getPasswordHash())) {
            throw new BusinessException(ResultCodes.UNAUTHORIZED, "账号或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCodes.UNAUTHORIZED, "账号或密码错误");
        }
        String token = tokenStore.issue(user.getId());
        return new LoginData(token, user.getId(), user.getUsername(), user.getRole());
    }

    @Override
    public void logout(String token) {
        tokenStore.revoke(token);
    }

    @Override
    public UserData me(Long userId) {
        UserAccountEntity user = requireUser(userId);
        return new UserData(user.getId(), user.getUsername(), user.getRole());
    }

    @Override
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        UserAccountEntity user = requireUser(userId);
        if (isBlank(currentPassword) || !PasswordHasher.verify(currentPassword, user.getPasswordHash())) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, "当前密码不正确。");
        }
        String violation = PasswordPolicy.validateNew(newPassword);
        if (violation != null) {
            throw new BusinessException(ResultCodes.VALIDATION_ERROR, violation);
        }
        String newHash = PasswordHasher.hash(newPassword, PasswordHasher.newSalt());
        userAccountRepository.updatePasswordHash(userId, newHash);
        // 改密后当前令牌立即失效：强制用新密码重新登录（PRD FR-2.5 的"请重新登录"语义）
        tokenStore.revokeByUser(userId);
    }

    private UserAccountEntity requireUser(Long userId) {
        UserAccountEntity user = userAccountRepository.findById(userId);
        if (user == null) {
            throw new BusinessException(ResultCodes.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }
        return user;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

}

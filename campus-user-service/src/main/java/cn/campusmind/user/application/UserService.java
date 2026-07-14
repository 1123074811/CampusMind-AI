package cn.campusmind.user.application;

import cn.campusmind.common.exception.BusinessException;
import cn.campusmind.user.controller.AdminUserListResponse;
import cn.campusmind.user.controller.AdminUserResponse;
import cn.campusmind.user.controller.CreateUserRequest;
import cn.campusmind.user.controller.ResetPasswordRequest;
import cn.campusmind.user.controller.ProfileTagsResponse;
import cn.campusmind.user.controller.UpdateProfileRequest;
import cn.campusmind.user.controller.UpdateProfileTagsRequest;
import cn.campusmind.user.controller.UpdateUserStatusRequest;
import cn.campusmind.user.controller.UserMeResponse;
import cn.campusmind.user.controller.UserProfileResponse;
import cn.campusmind.user.controller.UserDataExportResponse;
import cn.campusmind.user.controller.DeleteAccountRequest;
import cn.campusmind.user.domain.UserAccount;
import cn.campusmind.user.domain.UserProfile;
import cn.campusmind.user.infrastructure.mapper.UserAccountMapper;
import cn.campusmind.user.infrastructure.mapper.UserProfileMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.time.Duration;

@Service
public class UserService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final UserAccountMapper userAccountMapper;
    private final UserProfileMapper userProfileMapper;
    private final ObjectMapper objectMapper;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    public UserService(
            UserAccountMapper userAccountMapper,
            UserProfileMapper userProfileMapper,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            StringRedisTemplate redisTemplate
    ) {
        this.userAccountMapper = userAccountMapper;
        this.userProfileMapper = userProfileMapper;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
    }

    @Transactional(readOnly = true)
    public UserMeResponse getMe(CurrentUser currentUser) {
        UserAccount account = requireAccount(currentUser.userId());
        UserProfile profile = findProfile(currentUser.userId());
        return new UserMeResponse(
                account.getId(),
                account.getUsername(),
                maskPhone(account.getPhone()),
                account.getRole(),
                account.getStatus(),
                toProfileResponse(profile)
        );
    }

    @Transactional
    public UserProfileResponse updateProfile(CurrentUser currentUser, UpdateProfileRequest request) {
        requireAccount(currentUser.userId());
        UserProfile profile = findProfile(currentUser.userId());
        boolean creating = profile == null;
        if (creating) {
            profile = new UserProfile();
            profile.setUserId(currentUser.userId());
        }
        profile.setCollege(request.college());
        profile.setMajor(request.major());
        profile.setGrade(request.grade());
        profile.setClassName(request.className());
        profile.setInterestTags(toJson(request.interestTags()));
        profile.setCourseCodes(toJson(request.courseCodes()));

        if (creating) {
            userProfileMapper.insert(profile);
        } else {
            userProfileMapper.updateById(profile);
        }
        return toProfileResponse(findProfile(currentUser.userId()));
    }

    @Transactional(readOnly = true)
    public UserDataExportResponse exportMyData(CurrentUser currentUser) {
        UserAccount account = requireAccount(currentUser.userId());
        Long userId = account.getId();
        return new UserDataExportResponse(
                Instant.now(),
                new UserDataExportResponse.Account(
                        account.getId(), account.getUsername(), account.getPhone(), account.getRole(),
                        account.getStatus(), account.getCreatedAt(), account.getUpdatedAt()
                ),
                toProfileResponse(findProfile(userId)),
                jdbcTemplate.queryForList("SELECT * FROM user_information_state WHERE user_id = ?", userId),
                jdbcTemplate.queryForList("SELECT * FROM user_source_subscription WHERE user_id = ?", userId),
                jdbcTemplate.queryForList("SELECT * FROM user_action_item WHERE user_id = ?", userId),
                jdbcTemplate.queryForList("SELECT * FROM user_reminder WHERE user_id = ?", userId),
                jdbcTemplate.queryForList("SELECT * FROM campus_event WHERE owner_user_id = ?", userId)
        );
    }

    @Transactional
    public void deleteMyAccount(CurrentUser currentUser, DeleteAccountRequest request) {
        UserAccount account = requireAccount(currentUser.userId());
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new BusinessException("INVALID_CREDENTIALS", "密码错误", HttpStatus.UNAUTHORIZED);
        }
        Long userId = account.getId();
        jdbcTemplate.update("DELETE FROM user_reminder WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM user_action_item WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM user_information_state WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM user_source_subscription WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM campus_event WHERE owner_user_id = ?", userId);
        userProfileMapper.delete(new LambdaQueryWrapper<UserProfile>().eq(UserProfile::getUserId, userId));

        account.setUsername("deleted_" + userId + "_" + UUID.randomUUID().toString().substring(0, 8));
        account.setPhone(null);
        account.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        account.setStatus(0);
        userAccountMapper.updateById(account);
        // MyBatis-Plus 默认跳过 null 更新，隐私字段必须显式清空。
        jdbcTemplate.update("UPDATE `user` SET phone = NULL WHERE id = ?", userId);
        redisTemplate.opsForValue().set("auth:user-revoked:" + userId, "1", Duration.ofDays(30));
    }

    @Transactional(readOnly = true)
    public AdminUserListResponse adminUsers(CurrentUser currentUser, String keyword, String role, Integer status, int size) {
        requireAdmin(currentUser);
        int safeSize = Math.min(Math.max(size, 1), 100);
        LambdaQueryWrapper<UserAccount> query = new LambdaQueryWrapper<UserAccount>()
                .like(StringUtils.hasText(keyword), UserAccount::getUsername, keyword)
                .eq(StringUtils.hasText(role), UserAccount::getRole, role)
                .eq(status != null, UserAccount::getStatus, status)
                .orderByDesc(UserAccount::getCreatedAt)
                .orderByDesc(UserAccount::getId);
        Page<UserAccount> page = userAccountMapper.selectPage(Page.of(1, safeSize), query);
        return new AdminUserListResponse(
                page.getRecords().stream().map(this::toAdminUser).toList(),
                page.getTotal()
        );
    }

    @Transactional
    public AdminUserResponse createUser(CurrentUser currentUser, CreateUserRequest request) {
        requireAdmin(currentUser);
        Long existing = userAccountMapper.selectCount(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUsername, request.username()));
        if (existing != null && existing > 0) {
            throw new BusinessException("USERNAME_EXISTS", "用户名已存在", HttpStatus.CONFLICT);
        }
        UserAccount account = new UserAccount();
        account.setUsername(request.username());
        account.setPhone(request.phone());
        account.setRole(request.role());
        account.setStatus(1);
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        userAccountMapper.insert(account);
        return toAdminUser(userAccountMapper.selectById(account.getId()));
    }

    @Transactional
    public AdminUserResponse updateUserStatus(CurrentUser currentUser, Long userId, UpdateUserStatusRequest request) {
        requireAdmin(currentUser);
        UserAccount account = requireAccount(userId);
        account.setStatus(request.status());
        userAccountMapper.updateById(account);
        return toAdminUser(userAccountMapper.selectById(userId));
    }

    @Transactional
    public AdminUserResponse resetPassword(CurrentUser currentUser, Long userId, ResetPasswordRequest request) {
        requireAdmin(currentUser);
        UserAccount account = requireAccount(userId);
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        userAccountMapper.updateById(account);
        return toAdminUser(userAccountMapper.selectById(userId));
    }

    private UserAccount requireAccount(Long userId) {
        UserAccount account = userAccountMapper.selectById(userId);
        if (account == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND);
        }
        return account;
    }

    private void requireAdmin(CurrentUser currentUser) {
        if (currentUser == null || !"ADMIN".equals(currentUser.role())) {
            throw new BusinessException("ADMIN_REQUIRED", "需要管理员权限", HttpStatus.FORBIDDEN);
        }
        requireAccount(currentUser.userId());
    }

    private UserProfile findProfile(Long userId) {
        return userProfileMapper.selectOne(new LambdaQueryWrapper<UserProfile>()
                .eq(UserProfile::getUserId, userId));
    }

    private UserProfileResponse toProfileResponse(UserProfile profile) {
        if (profile == null) {
            return null;
        }
        return new UserProfileResponse(
                profile.getCollege(),
                profile.getMajor(),
                profile.getGrade(),
                profile.getClassName(),
                fromJson(profile.getInterestTags()),
                fromJson(profile.getCourseCodes()),
                profile.getUpdatedAt()
        );
    }

    private AdminUserResponse toAdminUser(UserAccount account) {
        return new AdminUserResponse(
                account.getId(),
                account.getUsername(),
                maskPhone(account.getPhone()),
                account.getRole(),
                account.getStatus(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("PROFILE_SERIALIZE_FAILED", "用户画像保存失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // ========== 用户画像标签与敏感度 ==========

    @Transactional(readOnly = true)
    public ProfileTagsResponse getProfileTags(CurrentUser currentUser) {
        UserProfile profile = findProfile(currentUser.userId());
        if (profile == null) {
            return new ProfileTagsResponse(List.of(), 0.5);
        }
        List<String> tags = fromJson(profile.getInterestTags());
        double sensitivity = profile.getSensitivity() != null ? profile.getSensitivity() : 0.5;
        return new ProfileTagsResponse(tags, sensitivity);
    }

    @Transactional
    public ProfileTagsResponse updateProfileTags(CurrentUser currentUser, List<String> tags, double sensitivity) {
        requireAccount(currentUser.userId());
        UserProfile profile = findProfile(currentUser.userId());
        boolean creating = profile == null;
        if (creating) {
            profile = new UserProfile();
            profile.setUserId(currentUser.userId());
        }
        profile.setInterestTags(toJson(tags));
        profile.setSensitivity(Math.max(0, Math.min(1, sensitivity)));
        if (creating) {
            userProfileMapper.insert(profile);
        } else {
            userProfileMapper.updateById(profile);
        }
        return new ProfileTagsResponse(
                fromJson(profile.getInterestTags()),
                profile.getSensitivity() != null ? profile.getSensitivity() : 0.5
        );
    }

    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}

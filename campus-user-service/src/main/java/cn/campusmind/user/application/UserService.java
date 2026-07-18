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
import cn.campusmind.user.controller.ConsentRequest;
import cn.campusmind.user.controller.PrivacyStatusResponse;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
    private final UserDataLifecycleClient dataLifecycleClient;

    public UserService(
            UserAccountMapper userAccountMapper,
            UserProfileMapper userProfileMapper,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate,
            StringRedisTemplate redisTemplate,
            UserDataLifecycleClient dataLifecycleClient
    ) {
        this.userAccountMapper = userAccountMapper;
        this.userProfileMapper = userProfileMapper;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.dataLifecycleClient = dataLifecycleClient;
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
        ensurePersonalizationNotRevoked(currentUser.userId());
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
                        account.getId(), account.getUsername(), account.getPhone(), account.getEmail(), account.getRole(),
                        account.getStatus(), account.getCreatedAt(), account.getUpdatedAt()
                ),
                toProfileResponse(findProfile(userId)),
                jdbcTemplate.queryForList("SELECT * FROM user_information_state WHERE user_id = ?", userId),
                jdbcTemplate.queryForList("SELECT * FROM user_source_subscription WHERE user_id = ?", userId),
                jdbcTemplate.queryForList("SELECT * FROM user_action_item WHERE user_id = ?", userId),
                jdbcTemplate.queryForList("SELECT * FROM user_reminder WHERE user_id = ?", userId),
                jdbcTemplate.queryForList("SELECT * FROM campus_event WHERE owner_user_id = ?", userId),
                jdbcTemplate.queryForList("""
                        SELECT * FROM event_audit_log WHERE operator_id = ? OR event_id IN (
                          SELECT id FROM campus_event WHERE owner_user_id = ?
                        ) ORDER BY created_at
                        """, userId, userId),
                jdbcTemplate.queryForList(
                        "SELECT * FROM data_source_version WHERE operator_id = ? ORDER BY created_at", userId),
                jdbcTemplate.queryForList("SELECT * FROM information_item WHERE submitted_by_user_id = ?", userId),
                jdbcTemplate.queryForList("SELECT * FROM import_task WHERE user_id = ?", userId),
                dataLifecycleClient.listRawDocuments(userId),
                jdbcTemplate.queryForList("SELECT * FROM user_consent_record WHERE user_id = ? ORDER BY occurred_at", userId),
                jdbcTemplate.queryForList("SELECT id, device_id, platform, enabled, created_at, updated_at FROM user_device WHERE user_id = ?", userId),
                jdbcTemplate.queryForList("SELECT * FROM notification_delivery WHERE user_id = ? ORDER BY created_at", userId)
        );
    }

    @Transactional
    public void deleteMyAccount(CurrentUser currentUser, DeleteAccountRequest request) {
        UserAccount account = requireAccount(currentUser.userId());
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new BusinessException("INVALID_CREDENTIALS", "密码错误", HttpStatus.UNAUTHORIZED);
        }
        Long userId = account.getId();
        List<String> vectorDocIds = jdbcTemplate.queryForList(
                "SELECT vector_doc_id FROM campus_event WHERE owner_user_id = ? AND vector_doc_id IS NOT NULL",
                String.class, userId);
        dataLifecycleClient.deleteRawDocuments(userId);
        dataLifecycleClient.deleteVectors(vectorDocIds);
        dataLifecycleClient.deleteChatMemory(userId);

        jdbcTemplate.update("DELETE FROM notification_delivery WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM user_device WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM user_consent_record WHERE user_id = ?", userId);
        jdbcTemplate.update("""
                DELETE FROM user_reminder WHERE action_item_id IN (
                  SELECT id FROM user_action_item WHERE information_item_id IN (
                    SELECT id FROM information_item WHERE submitted_by_user_id = ?
                  )
                )
                """, userId);
        jdbcTemplate.update("""
                DELETE FROM user_action_item WHERE information_item_id IN (
                  SELECT id FROM information_item WHERE submitted_by_user_id = ?
                )
                """, userId);
        jdbcTemplate.update("""
                DELETE FROM user_information_state WHERE item_id IN (
                  SELECT id FROM information_item WHERE submitted_by_user_id = ?
                )
                """, userId);
        jdbcTemplate.update("""
                DELETE FROM information_change_log WHERE item_id IN (
                  SELECT id FROM information_item WHERE submitted_by_user_id = ?
                )
                """, userId);
        jdbcTemplate.update("DELETE FROM user_reminder WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM user_action_item WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM user_information_state WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM user_source_subscription WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM information_item WHERE submitted_by_user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM import_task WHERE user_id = ?", userId);
        jdbcTemplate.update("""
                DELETE FROM event_source_ref WHERE event_id IN (
                  SELECT id FROM campus_event WHERE owner_user_id = ?
                )
                """, userId);
        jdbcTemplate.update("""
                DELETE FROM event_audit_log WHERE event_id IN (
                  SELECT id FROM campus_event WHERE owner_user_id = ?
                )
                """, userId);
        jdbcTemplate.update("UPDATE event_audit_log SET operator_id = NULL WHERE operator_id = ?", userId);
        jdbcTemplate.update("UPDATE data_source_version SET operator_id = NULL WHERE operator_id = ?", userId);
        jdbcTemplate.update("DELETE FROM campus_event WHERE owner_user_id = ?", userId);
        userProfileMapper.delete(new LambdaQueryWrapper<UserProfile>().eq(UserProfile::getUserId, userId));

        account.setUsername("deleted_" + userId + "_" + UUID.randomUUID().toString().substring(0, 8));
        account.setPhone(null);
        account.setEmail(null);
        account.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        account.setStatus(0);
        userAccountMapper.updateById(account);
        // MyBatis-Plus 默认跳过 null 更新，隐私字段必须显式清空。
        jdbcTemplate.update("UPDATE `user` SET phone = NULL, email = NULL WHERE id = ?", userId);
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
        if (request.status() == 0) revokeUserSessions(userId);
        return toAdminUser(userAccountMapper.selectById(userId));
    }

    @Transactional
    public AdminUserResponse resetPassword(CurrentUser currentUser, Long userId, ResetPasswordRequest request) {
        requireAdmin(currentUser);
        UserAccount account = requireAccount(userId);
        account.setPasswordHash(passwordEncoder.encode(request.password()));
        userAccountMapper.updateById(account);
        revokeUserSessions(userId);
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
        ensurePersonalizationNotRevoked(currentUser.userId());
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

    @Transactional
    public ProfileTagsResponse learnProfileTags(CurrentUser currentUser, List<String> learnedTags) {
        requireAccount(currentUser.userId());
        ensurePersonalizationNotRevoked(currentUser.userId());
        UserProfile profile = findProfile(currentUser.userId());
        boolean creating = profile == null;
        if (creating) {
            profile = new UserProfile();
            profile.setUserId(currentUser.userId());
            profile.setSensitivity(0.5);
        }
        Set<String> merged = new LinkedHashSet<>(fromJson(profile.getInterestTags()));
        if (learnedTags != null) {
            learnedTags.stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .limit(8)
                    .forEach(merged::add);
        }
        List<String> tags = merged.stream().limit(20).toList();
        profile.setInterestTags(toJson(tags));
        if (creating) {
            userProfileMapper.insert(profile);
        } else {
            userProfileMapper.updateById(profile);
        }
        return new ProfileTagsResponse(tags,
                profile.getSensitivity() != null ? profile.getSensitivity() : 0.5);
    }

    @Transactional(readOnly = true)
    public PrivacyStatusResponse privacyStatus(CurrentUser currentUser, String policyVersion, int retentionDays) {
        requireAccount(currentUser.userId());
        List<PrivacyStatusResponse.Consent> consents = jdbcTemplate.query("""
                SELECT c.id, c.consent_type, c.policy_version, c.granted, c.source, c.occurred_at
                FROM user_consent_record c
                JOIN (SELECT consent_type, MAX(id) id FROM user_consent_record WHERE user_id = ? GROUP BY consent_type) latest
                  ON latest.id = c.id
                ORDER BY c.consent_type
                """, (rs, rowNum) -> new PrivacyStatusResponse.Consent(
                rs.getLong("id"), rs.getString("consent_type"), rs.getString("policy_version"),
                rs.getBoolean("granted"), rs.getString("source"), rs.getTimestamp("occurred_at").toLocalDateTime()),
                currentUser.userId());
        return new PrivacyStatusResponse(policyVersion, retentionDays, consents);
    }

    @Transactional
    public PrivacyStatusResponse recordConsent(CurrentUser currentUser, ConsentRequest request,
                                               String policyVersion, int retentionDays) {
        requireAccount(currentUser.userId());
        jdbcTemplate.update("""
                INSERT INTO user_consent_record(user_id, consent_type, policy_version, granted, source)
                VALUES (?, ?, ?, ?, ?)
                """, currentUser.userId(), request.consentType(), request.policyVersion(), request.granted() ? 1 : 0,
                StringUtils.hasText(request.source()) ? request.source().trim().toUpperCase() : "APP");
        if ("PERSONALIZATION".equals(request.consentType()) && !request.granted()) {
            jdbcTemplate.update("UPDATE user_profile SET interest_tags = JSON_ARRAY(), course_codes = JSON_ARRAY() WHERE user_id = ?",
                    currentUser.userId());
        }
        return privacyStatus(currentUser, policyVersion, retentionDays);
    }

    private void ensurePersonalizationNotRevoked(Long userId) {
        List<Integer> grants = jdbcTemplate.query("""
                SELECT granted FROM user_consent_record WHERE user_id = ? AND consent_type = 'PERSONALIZATION'
                ORDER BY id DESC LIMIT 1
                """, (rs, rowNum) -> rs.getInt(1), userId);
        if (grants.isEmpty() || grants.get(0) == 0) {
            throw new BusinessException("PERSONALIZATION_CONSENT_REQUIRED", "请先授权个性化画像后再修改", HttpStatus.CONFLICT);
        }
    }

    private void revokeUserSessions(Long userId) {
        redisTemplate.opsForValue().set("auth:user-revoked:" + userId,
                String.valueOf(Instant.now().toEpochMilli()), Duration.ofDays(30));
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

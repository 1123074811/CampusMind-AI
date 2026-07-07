package cn.campusmind.user.application;

import cn.campusmind.common.exception.BusinessException;
import cn.campusmind.user.controller.UpdateProfileRequest;
import cn.campusmind.user.controller.UserMeResponse;
import cn.campusmind.user.controller.UserProfileResponse;
import cn.campusmind.user.domain.UserAccount;
import cn.campusmind.user.domain.UserProfile;
import cn.campusmind.user.infrastructure.mapper.UserAccountMapper;
import cn.campusmind.user.infrastructure.mapper.UserProfileMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final UserAccountMapper userAccountMapper;
    private final UserProfileMapper userProfileMapper;
    private final ObjectMapper objectMapper;

    public UserService(
            UserAccountMapper userAccountMapper,
            UserProfileMapper userProfileMapper,
            ObjectMapper objectMapper
    ) {
        this.userAccountMapper = userAccountMapper;
        this.userProfileMapper = userProfileMapper;
        this.objectMapper = objectMapper;
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

    private UserAccount requireAccount(Long userId) {
        UserAccount account = userAccountMapper.selectById(userId);
        if (account == null) {
            throw new BusinessException("USER_NOT_FOUND", "用户不存在", HttpStatus.NOT_FOUND);
        }
        return account;
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

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("PROFILE_SERIALIZE_FAILED", "用户画像保存失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
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

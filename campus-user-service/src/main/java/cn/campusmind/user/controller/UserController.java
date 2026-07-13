package cn.campusmind.user.controller;

import cn.campusmind.common.web.ApiResponse;
import cn.campusmind.user.application.AuthTokenService;
import cn.campusmind.user.application.CurrentUser;
import cn.campusmind.user.application.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final AuthTokenService authTokenService;
    private final UserService userService;

    public UserController(AuthTokenService authTokenService, UserService userService) {
        this.authTokenService = authTokenService;
        this.userService = userService;
    }

    @GetMapping("/me")
    public ApiResponse<UserMeResponse> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authTokenService.parseBearerToken(authorization);
        return ApiResponse.ok(userService.getMe(currentUser));
    }

    @PutMapping("/me/profile")
    public ApiResponse<UserProfileResponse> updateProfile(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        CurrentUser currentUser = authTokenService.parseBearerToken(authorization);
        return ApiResponse.ok(userService.updateProfile(currentUser, request));
    }

    @GetMapping("/admin")
    public ApiResponse<AdminUserListResponse> adminUsers(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "50") int size
    ) {
        CurrentUser currentUser = authTokenService.parseBearerToken(authorization);
        return ApiResponse.ok(userService.adminUsers(currentUser, keyword, role, status, size));
    }

    @PostMapping("/admin")
    public ApiResponse<AdminUserResponse> createUser(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody CreateUserRequest request
    ) {
        CurrentUser currentUser = authTokenService.parseBearerToken(authorization);
        return ApiResponse.ok(userService.createUser(currentUser, request));
    }

    @PutMapping("/admin/{id}/status")
    public ApiResponse<AdminUserResponse> updateUserStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequest request
    ) {
        CurrentUser currentUser = authTokenService.parseBearerToken(authorization);
        return ApiResponse.ok(userService.updateUserStatus(currentUser, id, request));
    }

    @PutMapping("/admin/{id}/password")
    public ApiResponse<AdminUserResponse> resetPassword(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long id,
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        CurrentUser currentUser = authTokenService.parseBearerToken(authorization);
        return ApiResponse.ok(userService.resetPassword(currentUser, id, request));
    }

    @GetMapping("/profile-tags")
    public ApiResponse<ProfileTagsResponse> getProfileTags(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        CurrentUser currentUser = authTokenService.parseBearerToken(authorization);
        return ApiResponse.ok(userService.getProfileTags(currentUser));
    }

    @PutMapping("/profile-tags")
    public ApiResponse<ProfileTagsResponse> updateProfileTags(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody UpdateProfileTagsRequest request) {
        CurrentUser currentUser = authTokenService.parseBearerToken(authorization);
        return ApiResponse.ok(userService.updateProfileTags(currentUser, request.tags(), request.sensitivity()));
    }
}

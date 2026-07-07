package cn.campusmind.user.controller;

import cn.campusmind.common.web.ApiResponse;
import cn.campusmind.user.application.AuthTokenService;
import cn.campusmind.user.application.CurrentUser;
import cn.campusmind.user.application.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
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
}

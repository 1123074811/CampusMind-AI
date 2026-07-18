package cn.campusmind.ai.feign;

import cn.campusmind.common.web.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "campus-user-service", contextId = "userProfileMemoryClient")
public interface UserProfileMemoryClient {

    @GetMapping("/api/v1/users/profile-tags")
    ApiResponse<ProfileMemory> getProfile();

    @PostMapping("/api/v1/users/profile-tags/learn")
    ApiResponse<ProfileMemory> learn(@RequestBody LearnProfileTagsRequest request);

    record ProfileMemory(List<String> tags, double sensitivity) {
        public static ProfileMemory empty() {
            return new ProfileMemory(List.of(), 0.5);
        }
    }

    record LearnProfileTagsRequest(List<String> tags) {
    }
}

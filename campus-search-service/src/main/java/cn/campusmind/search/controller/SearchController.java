package cn.campusmind.search.controller;

import cn.campusmind.common.web.ApiResponse;
import cn.campusmind.search.application.AuthTokenService;
import cn.campusmind.search.application.CurrentUser;
import cn.campusmind.search.application.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private final AuthTokenService authTokenService;
    private final SearchService searchService;

    public SearchController(AuthTokenService authTokenService, SearchService searchService) {
        this.authTokenService = authTokenService;
        this.searchService = searchService;
    }

    @GetMapping
    public ApiResponse<SearchResponse> search(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam String query,
            @RequestParam(defaultValue = "false") boolean usePersonalProfile
    ) {
        CurrentUser user = authTokenService.parseBearerToken(authorization);
        return ApiResponse.ok(searchService.search(user, query, usePersonalProfile));
    }
}

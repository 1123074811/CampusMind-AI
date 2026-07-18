package cn.campusmind.importing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

@ConfigurationProperties(prefix = "campus.import.xju-ehall")
public record XjuEhallProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("https://ehall.xju.edu.cn/new/index.html") String loginUrl,
        @DefaultValue({"authserver.xju.edu.cn", "ehall.xju.edu.cn"}) List<String> authHosts,
        List<String> dataHosts,
        @DefaultValue("1") int schemaVersion,
        @DefaultValue("2026-07-18-v1") String policyVersion,
        @DefaultValue("2097152") int maxPayloadBytes,
        @DefaultValue("500") int maxSourceItems,
        @DefaultValue("800") int maxExpandedEvents,
        @DefaultValue("15") int collectedAtSkewMinutes
) {
    public List<String> safeAuthHosts() {
        return authHosts == null ? List.of() : authHosts;
    }

    public List<String> safeDataHosts() {
        return dataHosts == null ? List.of() : dataHosts;
    }
}

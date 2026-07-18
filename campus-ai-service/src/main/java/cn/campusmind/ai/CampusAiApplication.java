package cn.campusmind.ai;

import cn.campusmind.ai.config.AiModeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "cn.campusmind")
@EnableConfigurationProperties(AiModeProperties.class)
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "cn.campusmind.ai.feign")
public class CampusAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusAiApplication.class, args);
    }
}

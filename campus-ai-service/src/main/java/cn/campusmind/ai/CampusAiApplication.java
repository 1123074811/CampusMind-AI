package cn.campusmind.ai;

import cn.campusmind.ai.config.AiModeProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "cn.campusmind")
@EnableConfigurationProperties(AiModeProperties.class)
public class CampusAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusAiApplication.class, args);
    }
}

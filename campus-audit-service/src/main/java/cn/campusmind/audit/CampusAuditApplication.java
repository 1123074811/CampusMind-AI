package cn.campusmind.audit;

import cn.campusmind.common.config.JwtAuthProperties;
import cn.campusmind.common.web.GlobalExceptionHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;

@MapperScan("cn.campusmind.audit.infrastructure.mapper")
@SpringBootApplication
@EnableConfigurationProperties(JwtAuthProperties.class)
@Import(GlobalExceptionHandler.class)
@EnableDiscoveryClient
public class CampusAuditApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusAuditApplication.class, args);
    }
}

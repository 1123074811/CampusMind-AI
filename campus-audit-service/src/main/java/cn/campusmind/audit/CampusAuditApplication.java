package cn.campusmind.audit;

import cn.campusmind.audit.config.AuditAuthProperties;
import cn.campusmind.common.web.GlobalExceptionHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@MapperScan("cn.campusmind.audit.infrastructure.mapper")
@SpringBootApplication
@EnableConfigurationProperties(AuditAuthProperties.class)
@Import(GlobalExceptionHandler.class)
public class CampusAuditApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusAuditApplication.class, args);
    }
}

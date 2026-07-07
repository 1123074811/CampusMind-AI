package cn.campusmind.audit;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("cn.campusmind.audit.infrastructure.mapper")
@SpringBootApplication
public class CampusAuditApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusAuditApplication.class, args);
    }
}

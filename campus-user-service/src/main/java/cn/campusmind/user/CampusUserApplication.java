package cn.campusmind.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "cn.campusmind")
@MapperScan("cn.campusmind.user.infrastructure.mapper")
public class CampusUserApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusUserApplication.class, args);
    }
}

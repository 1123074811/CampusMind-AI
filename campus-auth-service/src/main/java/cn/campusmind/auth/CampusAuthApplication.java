package cn.campusmind.auth;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "cn.campusmind")
@EnableDiscoveryClient
@MapperScan("cn.campusmind.auth.infrastructure.mapper")
public class CampusAuthApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusAuthApplication.class, args);
    }
}

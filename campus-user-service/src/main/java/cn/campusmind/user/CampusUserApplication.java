package cn.campusmind.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "cn.campusmind")
@EnableDiscoveryClient
@MapperScan("cn.campusmind.user.infrastructure.mapper")
@EnableScheduling
public class CampusUserApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusUserApplication.class, args);
    }
}

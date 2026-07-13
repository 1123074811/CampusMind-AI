package cn.campusmind.feed;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "cn.campusmind")
@EnableDiscoveryClient
@MapperScan("cn.campusmind.feed.infrastructure.mapper")
public class CampusFeedApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusFeedApplication.class, args);
    }
}

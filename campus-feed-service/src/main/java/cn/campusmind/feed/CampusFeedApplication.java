package cn.campusmind.feed;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "cn.campusmind")
@EnableDiscoveryClient
@EnableScheduling
@MapperScan("cn.campusmind.feed.infrastructure.mapper")
public class CampusFeedApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusFeedApplication.class, args);
    }
}

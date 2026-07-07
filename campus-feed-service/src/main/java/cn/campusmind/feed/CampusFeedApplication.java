package cn.campusmind.feed;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "cn.campusmind")
@MapperScan("cn.campusmind.feed.infrastructure.mapper")
public class CampusFeedApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusFeedApplication.class, args);
    }
}

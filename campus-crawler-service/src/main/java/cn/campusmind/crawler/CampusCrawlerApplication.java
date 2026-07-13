package cn.campusmind.crawler;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@MapperScan("cn.campusmind.crawler.infrastructure.mapper")
@ConfigurationPropertiesScan
@EnableScheduling
@SpringBootApplication
@EnableDiscoveryClient
public class CampusCrawlerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusCrawlerApplication.class, args);
    }
}

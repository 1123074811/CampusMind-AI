package cn.campusmind.event;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = "cn.campusmind")
@EnableDiscoveryClient
@MapperScan("cn.campusmind.event.infrastructure.mapper")
public class CampusEventApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusEventApplication.class, args);
    }
}

package cn.campusmind.search;

import cn.campusmind.common.config.JwtAuthProperties;
import cn.campusmind.search.config.SearchProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = "cn.campusmind")
@EnableConfigurationProperties({SearchProperties.class, JwtAuthProperties.class})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "cn.campusmind.search.feign")
@MapperScan("cn.campusmind.search.infrastructure.mapper")
public class CampusSearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusSearchApplication.class, args);
    }
}

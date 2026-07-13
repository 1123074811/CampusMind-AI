package cn.campusmind.importing;

import cn.campusmind.common.config.JwtAuthProperties;
import cn.campusmind.importing.config.ImportProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "cn.campusmind")
@EnableConfigurationProperties({ImportProperties.class, JwtAuthProperties.class})
@EnableScheduling
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "cn.campusmind.importing.feign")
@MapperScan("cn.campusmind.importing.infrastructure.mapper")
public class CampusImportApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusImportApplication.class, args);
    }
}

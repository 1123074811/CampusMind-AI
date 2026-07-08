package cn.campusmind.search;

import cn.campusmind.search.config.AuthProperties;
import cn.campusmind.search.config.SearchProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "cn.campusmind")
@EnableConfigurationProperties({SearchProperties.class, AuthProperties.class})
@MapperScan("cn.campusmind.search.infrastructure.mapper")
public class CampusSearchApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusSearchApplication.class, args);
    }
}

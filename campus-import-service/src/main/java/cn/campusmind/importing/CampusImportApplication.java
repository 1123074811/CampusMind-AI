package cn.campusmind.importing;

import cn.campusmind.importing.config.AuthProperties;
import cn.campusmind.importing.config.ImportProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(scanBasePackages = "cn.campusmind")
@EnableConfigurationProperties({ImportProperties.class, AuthProperties.class})
@MapperScan("cn.campusmind.importing.infrastructure.mapper")
public class CampusImportApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusImportApplication.class, args);
    }
}

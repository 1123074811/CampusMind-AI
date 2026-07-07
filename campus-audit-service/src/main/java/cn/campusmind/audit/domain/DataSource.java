package cn.campusmind.audit.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("data_source")
public class DataSource {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    @TableField("source_type")
    private String sourceType;
    @TableField("base_url")
    private String baseUrl;
    @TableField("parser_type")
    private String parserType;
    private Integer enabled;
    @TableField("last_crawled_at")
    private LocalDateTime lastCrawledAt;

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getParserType() {
        return parserType;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public LocalDateTime getLastCrawledAt() {
        return lastCrawledAt;
    }
}

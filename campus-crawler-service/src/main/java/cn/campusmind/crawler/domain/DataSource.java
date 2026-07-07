package cn.campusmind.crawler.domain;

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
    @TableField("robots_url")
    private String robotsUrl;
    @TableField("crawl_interval_seconds")
    private Integer crawlIntervalSeconds;
    @TableField("parser_type")
    private String parserType;
    @TableField("selector_config")
    private String selectorConfig;
    private Integer enabled;
    @TableField("last_crawled_at")
    private LocalDateTime lastCrawledAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;

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

    public String getRobotsUrl() {
        return robotsUrl;
    }

    public Integer getCrawlIntervalSeconds() {
        return crawlIntervalSeconds;
    }

    public String getParserType() {
        return parserType;
    }

    public String getSelectorConfig() {
        return selectorConfig;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public LocalDateTime getLastCrawledAt() {
        return lastCrawledAt;
    }

    public void setLastCrawledAt(LocalDateTime lastCrawledAt) {
        this.lastCrawledAt = lastCrawledAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}

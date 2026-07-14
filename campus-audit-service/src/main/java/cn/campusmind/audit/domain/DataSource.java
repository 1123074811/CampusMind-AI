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
    @TableField("robots_url")
    private String robotsUrl;
    @TableField("crawl_interval_seconds")
    private Integer crawlIntervalSeconds;
    @TableField("parser_type")
    private String parserType;
    private Integer enabled;
    @TableField("selector_config")
    private String selectorConfig;
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

    public String getRobotsUrl() { return robotsUrl; }
    public Integer getCrawlIntervalSeconds() { return crawlIntervalSeconds; }
    public String getSelectorConfig() { return selectorConfig; }
    public void setName(String name) { this.name = name; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public void setRobotsUrl(String robotsUrl) { this.robotsUrl = robotsUrl; }
    public void setCrawlIntervalSeconds(Integer crawlIntervalSeconds) { this.crawlIntervalSeconds = crawlIntervalSeconds; }
    public void setParserType(String parserType) { this.parserType = parserType; }
    public void setSelectorConfig(String selectorConfig) { this.selectorConfig = selectorConfig; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
}

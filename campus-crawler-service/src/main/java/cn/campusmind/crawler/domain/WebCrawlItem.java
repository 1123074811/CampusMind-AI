package cn.campusmind.crawler.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("web_crawl_item")
public class WebCrawlItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("task_id")
    private Long taskId;
    @TableField("source_id")
    private Long sourceId;
    @TableField("source_name")
    private String sourceName;
    @TableField("source_url")
    private String sourceUrl;
    @TableField("item_url")
    private String itemUrl;
    private String title;
    @TableField("date_text")
    private String dateText;
    private String summary;
    @TableField("content_hash")
    private String contentHash;
    @TableField("parser_version")
    private String parserVersion;
    @TableField("fetched_at")
    private LocalDateTime fetchedAt;

    public Long getId() {
        return id;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public void setItemUrl(String itemUrl) {
        this.itemUrl = itemUrl;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setDateText(String dateText) {
        this.dateText = dateText;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public void setParserVersion(String parserVersion) {
        this.parserVersion = parserVersion;
    }

    public void setFetchedAt(LocalDateTime fetchedAt) {
        this.fetchedAt = fetchedAt;
    }
}

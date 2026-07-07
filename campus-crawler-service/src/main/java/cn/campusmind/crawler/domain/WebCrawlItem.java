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
    @TableField("detail_title")
    private String detailTitle;
    @TableField("date_text")
    private String dateText;
    private String summary;
    @TableField("detail_content")
    private String detailContent;
    @TableField("content_hash")
    private String contentHash;
    @TableField("parser_version")
    private String parserVersion;
    @TableField("detail_http_status")
    private Integer detailHttpStatus;
    @TableField("detail_fetched_at")
    private LocalDateTime detailFetchedAt;
    @TableField("detail_content_hash")
    private String detailContentHash;
    @TableField("parse_status")
    private String parseStatus;
    @TableField("parse_error")
    private String parseError;
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

    public void setDetailTitle(String detailTitle) {
        this.detailTitle = detailTitle;
    }

    public void setDateText(String dateText) {
        this.dateText = dateText;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public void setDetailContent(String detailContent) {
        this.detailContent = detailContent;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public void setParserVersion(String parserVersion) {
        this.parserVersion = parserVersion;
    }

    public void setDetailHttpStatus(Integer detailHttpStatus) {
        this.detailHttpStatus = detailHttpStatus;
    }

    public void setDetailFetchedAt(LocalDateTime detailFetchedAt) {
        this.detailFetchedAt = detailFetchedAt;
    }

    public void setDetailContentHash(String detailContentHash) {
        this.detailContentHash = detailContentHash;
    }

    public void setParseStatus(String parseStatus) {
        this.parseStatus = parseStatus;
    }

    public void setParseError(String parseError) {
        this.parseError = parseError;
    }

    public void setFetchedAt(LocalDateTime fetchedAt) {
        this.fetchedAt = fetchedAt;
    }
}

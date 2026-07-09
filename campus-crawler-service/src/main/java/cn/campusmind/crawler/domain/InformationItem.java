package cn.campusmind.crawler.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("information_item")
public class InformationItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("source_id")
    private Long sourceId;

    @TableField("source_name")
    private String sourceName;

    @TableField("source_url")
    private String sourceUrl;

    @TableField("item_url")
    private String itemUrl;

    private String title;

    @TableField("publish_time")
    private LocalDateTime publishTime;

    @TableField("fetched_at")
    private LocalDateTime fetchedAt;

    @TableField("detail_content")
    private String detailContent;

    @TableField("content_hash")
    private String contentHash;

    @TableField("item_status")
    private String itemStatus;

    @TableField("parse_status")
    private String parseStatus;

    @TableField("parse_error")
    private String parseError;

    public Long getId() {
        return id;
    }

    public String getItemUrl() {
        return itemUrl;
    }

    public String getTitle() {
        return title;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setId(Long id) {
        this.id = id;
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

    public void setPublishTime(LocalDateTime publishTime) {
        this.publishTime = publishTime;
    }

    public void setFetchedAt(LocalDateTime fetchedAt) {
        this.fetchedAt = fetchedAt;
    }

    public void setDetailContent(String detailContent) {
        this.detailContent = detailContent;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public void setItemStatus(String itemStatus) {
        this.itemStatus = itemStatus;
    }

    public void setParseStatus(String parseStatus) {
        this.parseStatus = parseStatus;
    }

    public void setParseError(String parseError) {
        this.parseError = parseError;
    }
}

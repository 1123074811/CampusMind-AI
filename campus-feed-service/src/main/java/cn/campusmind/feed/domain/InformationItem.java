package cn.campusmind.feed.domain;

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

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getItemUrl() {
        return itemUrl;
    }

    public String getTitle() {
        return title;
    }

    public LocalDateTime getPublishTime() {
        return publishTime;
    }

    public LocalDateTime getFetchedAt() {
        return fetchedAt;
    }

    public String getDetailContent() {
        return detailContent;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getItemStatus() {
        return itemStatus;
    }

    public String getParseStatus() {
        return parseStatus;
    }

    public String getParseError() {
        return parseError;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}

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

    @TableField("ai_status")
    private String aiStatus;
    @TableField("ai_event_type")
    private String aiEventType;
    @TableField("ai_summary")
    private String aiSummary;
    @TableField("ai_card_json")
    private String aiCardJson;
    @TableField("ai_need_review")
    private Boolean aiNeedReview;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public Long getSourceId() {
        return sourceId;
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

    public String getAiStatus() { return aiStatus; }
    public String getAiEventType() { return aiEventType; }
    public String getAiSummary() { return aiSummary; }
    public String getAiCardJson() { return aiCardJson; }
    public Boolean getAiNeedReview() { return aiNeedReview; }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setTitle(String title) { this.title = title; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public void setSourceName(String sourceName) { this.sourceName = sourceName; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public void setItemUrl(String itemUrl) { this.itemUrl = itemUrl; }
    public void setDetailContent(String detailContent) { this.detailContent = detailContent; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public void setFetchedAt(LocalDateTime fetchedAt) { this.fetchedAt = fetchedAt; }
    public void setItemStatus(String itemStatus) { this.itemStatus = itemStatus; }
    public void setParseStatus(String parseStatus) { this.parseStatus = parseStatus; }
    public void setAiStatus(String aiStatus) { this.aiStatus = aiStatus; }
    public void setAiSummary(String aiSummary) { this.aiSummary = aiSummary; }
    public void setAiEventType(String aiEventType) { this.aiEventType = aiEventType; }
    public void setAiCardJson(String aiCardJson) { this.aiCardJson = aiCardJson; }
    public void setAiNeedReview(Boolean aiNeedReview) { this.aiNeedReview = aiNeedReview; }
}

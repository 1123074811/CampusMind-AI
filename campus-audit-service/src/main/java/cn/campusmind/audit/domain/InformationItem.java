package cn.campusmind.audit.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("information_item")
public class InformationItem {

    @TableId(type = IdType.AUTO)
    private Long id;
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
    @TableField("item_status")
    private String itemStatus;
    @TableField("parse_status")
    private String parseStatus;
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
    @TableField("submitted_by")
    private String submittedBy;
    @TableField("submitted_by_user_id")
    private Long submittedByUserId;

    public Long getId() { return id; }
    public String getSourceName() { return sourceName; }
    public String getSourceUrl() { return sourceUrl; }
    public String getItemUrl() { return itemUrl; }
    public String getTitle() { return title; }
    public LocalDateTime getPublishTime() { return publishTime; }
    public LocalDateTime getFetchedAt() { return fetchedAt; }
    public String getDetailContent() { return detailContent; }
    public String getItemStatus() { return itemStatus; }
    public String getParseStatus() { return parseStatus; }
    public String getAiStatus() { return aiStatus; }
    public String getAiEventType() { return aiEventType; }
    public String getAiSummary() { return aiSummary; }
    public String getAiCardJson() { return aiCardJson; }
    public Boolean getAiNeedReview() { return aiNeedReview; }
    public String getSubmittedBy() { return submittedBy; }
    public Long getSubmittedByUserId() { return submittedByUserId; }
    public void setItemStatus(String itemStatus) { this.itemStatus = itemStatus; }
    public void setTitle(String title) { this.title = title; }
    public void setAiSummary(String aiSummary) { this.aiSummary = aiSummary; }
    public void setAiEventType(String aiEventType) { this.aiEventType = aiEventType; }
    public void setDetailContent(String detailContent) { this.detailContent = detailContent; }
}

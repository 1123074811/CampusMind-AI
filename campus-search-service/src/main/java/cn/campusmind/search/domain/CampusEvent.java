package cn.campusmind.search.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("campus_event")
public class CampusEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String summary;

    @TableField("event_type")
    private String eventType;

    @TableField("source_type")
    private String sourceType;

    private String status;

    private BigDecimal confidence;

    @TableField("start_time")
    private LocalDateTime startTime;

    @TableField("end_time")
    private LocalDateTime endTime;

    private String location;

    private String organizer;

    @TableField("target_scope")
    private String targetScope;

    private String tags;

    @TableField("dedup_key")
    private String dedupKey;

    @TableField("vector_doc_id")
    private String vectorDocId;

    @TableField("published_at")
    private LocalDateTime publishedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getEventType() {
        return eventType;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getStatus() {
        return status;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public String getLocation() {
        return location;
    }

    public String getOrganizer() {
        return organizer;
    }

    public String getTargetScope() {
        return targetScope;
    }

    public String getTags() {
        return tags;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

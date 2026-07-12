package cn.campusmind.feed.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

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

    @TableField("start_time")
    private LocalDateTime startTime;

    private String location;

    private String tags;

    @TableField("target_scope")
    private String targetScope;

    @TableField("published_at")
    private LocalDateTime publishedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

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

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public String getLocation() {
        return location;
    }

    public String getTags() {
        return tags;
    }

    public String getTargetScope() {
        return targetScope;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

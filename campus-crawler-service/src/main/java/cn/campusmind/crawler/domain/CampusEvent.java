package cn.campusmind.crawler.domain;

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
    private String organizer;
    @TableField("start_time")
    private LocalDateTime startTime;
    @TableField("target_scope")
    private String targetScope;
    private String tags;
    @TableField("dedup_key")
    private String dedupKey;
    @TableField("published_at")
    private LocalDateTime publishedAt;

    public Long getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setOrganizer(String organizer) {
        this.organizer = organizer;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public void setTargetScope(String targetScope) {
        this.targetScope = targetScope;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public void setDedupKey(String dedupKey) {
        this.dedupKey = dedupKey;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }
}

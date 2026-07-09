package cn.campusmind.audit.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("event_audit_log")
public class EventAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("event_id")
    private Long eventId;
    @TableField("operator_id")
    private Long operatorId;
    private String action;
    @TableField("before_snapshot")
    private String beforeSnapshot;
    @TableField("after_snapshot")
    private String afterSnapshot;
    private String comment;
    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public Long getEventId() {
        return eventId;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public String getAction() {
        return action;
    }

    public String getBeforeSnapshot() {
        return beforeSnapshot;
    }

    public String getAfterSnapshot() {
        return afterSnapshot;
    }

    public String getComment() {
        return comment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public void setBeforeSnapshot(String beforeSnapshot) {
        this.beforeSnapshot = beforeSnapshot;
    }

    public void setAfterSnapshot(String afterSnapshot) {
        this.afterSnapshot = afterSnapshot;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}

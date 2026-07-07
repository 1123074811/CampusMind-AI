package cn.campusmind.audit.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("event_source_ref")
public class EventSourceRef {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("event_id")
    private Long eventId;
    @TableField("source_id")
    private Long sourceId;
    @TableField("source_title")
    private String sourceTitle;

    public Long getId() {
        return id;
    }

    public Long getEventId() {
        return eventId;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public String getSourceTitle() {
        return sourceTitle;
    }
}

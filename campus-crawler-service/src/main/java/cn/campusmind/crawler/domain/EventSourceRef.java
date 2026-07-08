package cn.campusmind.crawler.domain;

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
    @TableField("raw_doc_id")
    private String rawDocId;
    @TableField("source_url")
    private String sourceUrl;
    @TableField("source_title")
    private String sourceTitle;
    @TableField("content_hash")
    private String contentHash;

    public String getRawDocId() {
        return rawDocId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public void setSourceId(Long sourceId) {
        this.sourceId = sourceId;
    }

    public void setRawDocId(String rawDocId) {
        this.rawDocId = rawDocId;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }

    public void setSourceTitle(String sourceTitle) {
        this.sourceTitle = sourceTitle;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }
}

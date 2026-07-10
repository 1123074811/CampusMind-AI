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
    public void setItemStatus(String itemStatus) { this.itemStatus = itemStatus; }
}

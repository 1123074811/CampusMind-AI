package cn.campusmind.audit.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("crawl_task")
public class CrawlTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("source_id")
    private Long sourceId;
    @TableField("task_status")
    private String taskStatus;
    @TableField("crawl_url")
    private String crawlUrl;
    @TableField("http_status")
    private Integer httpStatus;
    @TableField("fail_reason")
    private String failReason;
    @TableField("started_at")
    private LocalDateTime startedAt;
    @TableField("finished_at")
    private LocalDateTime finishedAt;

    public Long getId() {
        return id;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public String getTaskStatus() {
        return taskStatus;
    }

    public String getCrawlUrl() {
        return crawlUrl;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public String getFailReason() {
        return failReason;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }
}

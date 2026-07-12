package cn.campusmind.audit.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("import_task")
public class ImportTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("user_id")
    private Long userId;
    @TableField("import_type")
    private String importType;
    @TableField("task_status")
    private String taskStatus;
    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getImportType() {
        return importType;
    }

    public String getTaskStatus() {
        return taskStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

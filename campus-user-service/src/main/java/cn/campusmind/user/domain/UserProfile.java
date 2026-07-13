package cn.campusmind.user.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("user_profile")
public class UserProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    private String college;

    private String major;

    private String grade;

    @TableField("class_name")
    private String className;

    @TableField("interest_tags")
    private String interestTags;

    @TableField("course_codes")
    private String courseCodes;

    private Double sensitivity;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getCollege() {
        return college;
    }

    public void setCollege(String college) {
        this.college = college;
    }

    public String getMajor() {
        return major;
    }

    public void setMajor(String major) {
        this.major = major;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getInterestTags() {
        return interestTags;
    }

    public void setInterestTags(String interestTags) {
        this.interestTags = interestTags;
    }

    public String getCourseCodes() {
        return courseCodes;
    }

    public void setCourseCodes(String courseCodes) {
        this.courseCodes = courseCodes;
    }

    public Double getSensitivity() {
        return sensitivity;
    }

    public void setSensitivity(Double sensitivity) {
        this.sensitivity = sensitivity;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}

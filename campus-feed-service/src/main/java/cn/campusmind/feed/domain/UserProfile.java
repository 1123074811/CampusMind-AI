package cn.campusmind.feed.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("user_profile")
public class UserProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    private String college;

    private String major;

    private String grade;

    @TableField("interest_tags")
    private String interestTags;

    @TableField("course_codes")
    private String courseCodes;

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getCollege() {
        return college;
    }

    public String getMajor() {
        return major;
    }

    public String getGrade() {
        return grade;
    }

    public String getInterestTags() {
        return interestTags;
    }

    public String getCourseCodes() {
        return courseCodes;
    }
}

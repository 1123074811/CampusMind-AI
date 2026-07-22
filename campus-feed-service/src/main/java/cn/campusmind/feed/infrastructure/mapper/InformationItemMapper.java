package cn.campusmind.feed.infrastructure.mapper;

import cn.campusmind.feed.domain.InformationItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface InformationItemMapper extends BaseMapper<InformationItem> {

    @Select("""
            <script>
            SELECT i.*
            FROM information_item i
            WHERE i.item_status IN ('ACTIVE', 'UPDATED')
              AND i.parse_status = 'DETAIL_SUCCESS'
              AND (i.source_name &lt;&gt; '雨课堂导入'
                OR (i.ai_event_type IN ('NOTICE', 'HOMEWORK', 'EXAM')
                  AND i.publish_time IS NOT NULL))
              AND (i.submitted_by_user_id IS NULL
                OR (#{userId} IS NOT NULL AND i.submitted_by_user_id = #{userId}))
            <if test="onlySubscribed">
              AND #{userId} IS NOT NULL
              AND EXISTS (
                SELECT 1 FROM user_source_subscription s
                WHERE s.user_id = #{userId} AND s.source_id = i.source_id AND s.enabled = 1
              )
            </if>
            ORDER BY COALESCE(i.publish_time, i.fetched_at) DESC, i.id DESC
            </script>
            """)
    List<InformationItem> selectFeedCandidates(@Param("userId") Long userId,
                                               @Param("onlySubscribed") boolean onlySubscribed);
}

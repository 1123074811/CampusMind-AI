package cn.campusmind.feed.infrastructure.mapper;

import cn.campusmind.feed.domain.InformationItem;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

public interface InformationItemMapper extends BaseMapper<InformationItem> {

    @Select("""
            <script>
            SELECT i.*
            FROM information_item i
            WHERE i.item_status IN ('ACTIVE', 'UPDATED')
              AND i.parse_status = 'DETAIL_SUCCESS'
            <if test="onlySubscribed">
              AND #{userId} IS NOT NULL
              AND EXISTS (
                SELECT 1 FROM user_source_subscription s
                WHERE s.user_id = #{userId} AND s.source_id = i.source_id AND s.enabled = 1
              )
            </if>
            <if test="cursor != null and cursorSubscriptionMatch == null">
              AND (i.fetched_at &lt; #{cursor}
                <if test="cursorId != null">
                  OR (i.fetched_at = #{cursor} AND i.id &lt; #{cursorId})
                </if>
              )
            </if>
            <if test="cursor != null and cursorSubscriptionMatch != null">
              AND (
                CASE WHEN #{userId} IS NOT NULL AND EXISTS (
                  SELECT 1 FROM user_source_subscription cs
                  WHERE cs.user_id = #{userId} AND cs.source_id = i.source_id AND cs.enabled = 1
                ) THEN 1 ELSE 0 END &lt; #{cursorSubscriptionMatch}
                OR (CASE WHEN #{userId} IS NOT NULL AND EXISTS (
                  SELECT 1 FROM user_source_subscription cs
                  WHERE cs.user_id = #{userId} AND cs.source_id = i.source_id AND cs.enabled = 1
                ) THEN 1 ELSE 0 END = #{cursorSubscriptionMatch} AND i.fetched_at &lt; #{cursor})
                OR (CASE WHEN #{userId} IS NOT NULL AND EXISTS (
                  SELECT 1 FROM user_source_subscription cs
                  WHERE cs.user_id = #{userId} AND cs.source_id = i.source_id AND cs.enabled = 1
                ) THEN 1 ELSE 0 END = #{cursorSubscriptionMatch}
                  AND i.fetched_at = #{cursor} AND i.id &lt; #{cursorId})
              )
            </if>
            ORDER BY CASE WHEN #{userId} IS NOT NULL AND EXISTS (
              SELECT 1 FROM user_source_subscription os
              WHERE os.user_id = #{userId} AND os.source_id = i.source_id AND os.enabled = 1
            ) THEN 1 ELSE 0 END DESC,
              i.fetched_at DESC, i.id DESC
            LIMIT #{limit}
            </script>
            """)
    List<InformationItem> selectRankedFeed(@Param("userId") Long userId,
                                           @Param("onlySubscribed") boolean onlySubscribed,
                                           @Param("cursor") LocalDateTime cursor,
                                           @Param("cursorId") Long cursorId,
                                           @Param("cursorSubscriptionMatch") Integer cursorSubscriptionMatch,
                                           @Param("limit") int limit);
}

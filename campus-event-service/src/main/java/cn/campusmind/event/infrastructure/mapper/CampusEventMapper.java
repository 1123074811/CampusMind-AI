package cn.campusmind.event.infrastructure.mapper;

import cn.campusmind.event.domain.CampusEvent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface CampusEventMapper extends BaseMapper<CampusEvent> {

    @Select("""
            SELECT e.*
            FROM campus_event e
            JOIN event_source_ref r ON r.event_id = e.id
            WHERE r.content_hash = #{contentHash}
              AND e.visibility = #{visibility}
              AND (#{visibility} = 'PUBLIC' OR e.source_type = #{sourceType})
              AND ((#{ownerUserId} IS NULL AND e.owner_user_id IS NULL)
                   OR e.owner_user_id = #{ownerUserId})
            LIMIT 1
            """)
    CampusEvent findByContentHashAndScope(@Param("contentHash") String contentHash,
                                          @Param("visibility") String visibility,
                                          @Param("sourceType") String sourceType,
                                          @Param("ownerUserId") Long ownerUserId);
}

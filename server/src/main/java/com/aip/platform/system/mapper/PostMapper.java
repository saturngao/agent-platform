package com.aip.platform.system.mapper;

import com.aip.platform.system.entity.Post;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 岗位数据访问：基础能力由 BaseMapper 提供
 */
public interface PostMapper extends BaseMapper<Post> {

    /**
     * 查询用户已分配的岗位ID
     *
     * @param userId 用户ID
     * @return 岗位ID集合
     */
    @Select("SELECT post_id FROM sys_user_post WHERE user_id = #{userId}")
    List<Long> selectPostIdsByUserId(@Param("userId") Long userId);
}

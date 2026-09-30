package com.aip.platform.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.aip.platform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 岗位
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_post")
public class Post extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "post_id", type = IdType.AUTO)
    private Long postId;

    private String postCode;

    private String postName;

    private Integer postSort;

    private Integer status;
}

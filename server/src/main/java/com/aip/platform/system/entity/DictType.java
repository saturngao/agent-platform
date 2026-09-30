package com.aip.platform.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.aip.platform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典类型
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_type")
public class DictType extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "dict_id", type = IdType.AUTO)
    private Long dictId;

    private String dictName;

    private String dictType;

    private Integer status;
}

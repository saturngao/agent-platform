package com.aip.platform.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.aip.platform.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典数据
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_dict_data")
public class DictData extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(value = "dict_code", type = IdType.AUTO)
    private Long dictCode;

    private String dictType;

    private String dictLabel;

    private String dictValue;

    private Integer dictSort;

    private String listClass;

    private Integer isDefault;

    private Integer status;
}

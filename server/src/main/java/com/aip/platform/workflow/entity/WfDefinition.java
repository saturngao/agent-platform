package com.aip.platform.workflow.entity;

import com.aip.platform.common.BaseEntity;
import com.aip.platform.workflow.dto.GraphDef;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工作流定义
 *
 * <p>对应表 wf_definition。该表没有 del_flag，删除为物理删除，因此不加 @TableLogic。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_definition")
public class WfDefinition extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 状态：0草稿 1发布 2停用 */
    public static final int STATUS_DRAFT = 0;
    public static final int STATUS_PUBLISHED = 1;
    public static final int STATUS_DISABLED = 2;

    @TableId(value = "def_id", type = IdType.AUTO)
    private Long defId;

    /** 流程唯一编码 */
    private String defCode;

    /** 流程名称 */
    private String defName;

    /** 分类 */
    private String category;

    /** 图标 */
    private String icon;

    /** 描述 */
    private String description;

    /** 画布定义 JSON：{nodes,edges} */
    private String graphJson;

    /** 入参定义 JSON 数组 */
    private String inputSchema;

    /** 版本号，每次发布 +1 */
    private Integer version;

    /** 状态：0草稿 1发布 2停用 */
    private Integer status;

    /** 是否曾发布 */
    private Integer published;

    /** 归属部门 */
    private Long deptId;

    // ---------------- 非数据库字段 ----------------

    /** wf_definition 无 remark 列，屏蔽基类字段避免写入 SQL */
    @TableField(exist = false)
    private String remark;

    /** 前端兼容别名：defKey -> defCode */
    @TableField(exist = false)
    private String defKey;

    /** 画布对象，与 graph_json 互转 */
    @TableField(exist = false)
    private GraphDef graph;

    /** 列表展示：累计运行次数 */
    @TableField(exist = false)
    private Integer runCount;

    /** 查询条件：创建时间起（yyyy-MM-dd HH:mm:ss） */
    @TableField(exist = false)
    private String beginTime;

    /** 查询条件：创建时间止（yyyy-MM-dd HH:mm:ss） */
    @TableField(exist = false)
    private String endTime;

    /** 挂载的技能ID列表（非表字段，读写 skill_binding） */
    @TableField(exist = false)
    private java.util.List<Long> skillIds;

    /** 挂载的技能名称，列表展示用 */
    @TableField(exist = false)
    private String skillNames;
}

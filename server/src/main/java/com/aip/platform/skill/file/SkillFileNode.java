package com.aip.platform.skill.file;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 技能目录下的文件节点
 *
 * <p>path 统一使用相对技能根目录的路径，目录分隔符统一为 /，前端可直接用于读写接口。</p>
 */
@Data
public class SkillFileNode {

    /** 文件或目录名 */
    private String name;

    /** 相对技能根目录的路径，如 scripts/cost.py */
    private String path;

    /** 是否目录 */
    private Boolean dir;

    /** 文件字节数，目录为 null */
    private Long size;

    /** 最后修改时间（毫秒时间戳） */
    private Long updateTime;

    /** 子节点，文件为空数组 */
    private List<SkillFileNode> children = new ArrayList<>();

    public static SkillFileNode file(String name, String path, Long size, Long updateTime) {
        SkillFileNode node = new SkillFileNode();
        node.setName(name);
        node.setPath(path);
        node.setDir(false);
        node.setSize(size);
        node.setUpdateTime(updateTime);
        return node;
    }

    public static SkillFileNode directory(String name, String path, Long updateTime) {
        SkillFileNode node = new SkillFileNode();
        node.setName(name);
        node.setPath(path);
        node.setDir(true);
        node.setUpdateTime(updateTime);
        return node;
    }
}

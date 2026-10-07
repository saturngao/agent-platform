package com.aip.platform.skill.service;

import com.aip.platform.skill.entity.Skill;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 技能导入：把用户写好的技能目录（SKILL.md + scripts/）整包导入平台
 *
 * <p>支持两种投递方式：</p>
 * <ul>
 *   <li>文件夹上传：前端用 webkitdirectory 选中整个目录，按相对路径逐个上传</li>
 *   <li>ZIP 上传：用户把目录打包后上传，服务端解压</li>
 * </ul>
 */
public interface ISkillImportService {

    /**
     * 导入一组带相对路径的文件
     *
     * @param files      上传的文件
     * @param relPaths   与 files 同序的相对路径；为空时退化为文件名
     * @param skillCode  期望的技能编码，为空时自动推导
     * @param overwrite  编码冲突时是否覆盖已有技能
     * @return 导入后的技能
     */
    Skill importFromFiles(List<MultipartFile> files, List<String> relPaths, String skillCode, boolean overwrite);

    /**
     * 导入一个 ZIP 包
     *
     * @param in         ZIP 流
     * @param originName 原始文件名，用于兜底推导技能名
     * @param skillCode  期望的技能编码，为空时自动推导
     * @param overwrite  编码冲突时是否覆盖已有技能
     * @return 导入后的技能
     */
    Skill importFromZip(InputStream in, String originName, String skillCode, boolean overwrite);

    /**
     * 校验一个待导入的技能包，返回解析出的元信息，供前端导入前预览
     *
     * @return 预览信息：skillCode / skillName / summary / 文件列表 / 脚本列表
     */
    Map<String, Object> preview(InputStream in);
}

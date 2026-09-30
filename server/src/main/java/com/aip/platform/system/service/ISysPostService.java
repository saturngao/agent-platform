package com.aip.platform.system.service;

import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.system.entity.Post;

import java.util.List;

/**
 * 岗位业务接口
 */
public interface ISysPostService {

    /**
     * 分页查询岗位
     *
     * @param query 分页参数
     * @param post  查询条件
     * @return 分页结果
     */
    PageResult<Post> selectPostPage(PageQuery query, Post post);

    /**
     * 不分页查询岗位列表
     *
     * @param post 查询条件
     * @return 岗位列表
     */
    List<Post> selectPostList(Post post);

    /**
     * 按ID查询岗位
     *
     * @param postId 岗位ID
     * @return 岗位
     */
    Post selectPostById(Long postId);

    /**
     * 查询用户已分配的岗位ID
     *
     * @param userId 用户ID
     * @return 岗位ID集合
     */
    List<Long> selectPostIdsByUserId(Long userId);

    /**
     * 新增岗位
     *
     * @param post 岗位
     * @return 影响行数
     */
    int insertPost(Post post);

    /**
     * 修改岗位
     *
     * @param post 岗位
     * @return 影响行数
     */
    int updatePost(Post post);

    /**
     * 批量删除岗位
     *
     * @param postIds 岗位ID集合
     * @return 影响行数
     */
    int deletePosts(List<Long> postIds);

    /**
     * 校验岗位编码是否唯一
     *
     * @param post 岗位
     * @return true 唯一
     */
    boolean checkPostCodeUnique(Post post);

    /**
     * 校验岗位名称是否唯一
     *
     * @param post 岗位
     * @return true 唯一
     */
    boolean checkPostNameUnique(Post post);
}

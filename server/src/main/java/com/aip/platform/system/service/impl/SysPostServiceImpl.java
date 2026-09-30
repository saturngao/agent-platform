package com.aip.platform.system.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.aip.platform.common.BusinessException;
import com.aip.platform.common.PageQuery;
import com.aip.platform.common.PageResult;
import com.aip.platform.system.entity.Post;
import com.aip.platform.system.mapper.PostMapper;
import com.aip.platform.system.service.ISysPostService;
import com.aip.platform.system.vo.PageParam;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 岗位业务实现
 */
@Service
public class SysPostServiceImpl extends ServiceImpl<PostMapper, Post> implements ISysPostService {

    @Override
    public PageResult<Post> selectPostPage(PageQuery query, Post post) {
        if (PageParam.isNoPaging(query)) {
            List<Post> all = selectPostList(post);
            return PageResult.page(all, 1, Math.max(all.size(), 1));
        }
        IPage<Post> page = new Page<>(query.getPageNum(), query.getPageSize());
        IPage<Post> result = baseMapper.selectPage(page, buildQueryWrapper(post));
        return PageResult.of(result.getRecords(), result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public List<Post> selectPostList(Post post) {
        return this.list(buildQueryWrapper(post));
    }

    @Override
    public Post selectPostById(Long postId) {
        return postId == null ? null : baseMapper.selectById(postId);
    }

    @Override
    public List<Long> selectPostIdsByUserId(Long userId) {
        return userId == null ? List.of() : baseMapper.selectPostIdsByUserId(userId);
    }

    @Override
    public int insertPost(Post post) {
        validatePost(post);
        if (post.getPostSort() == null) {
            post.setPostSort(0);
        }
        if (post.getStatus() == null) {
            post.setStatus(0);
        }
        return baseMapper.insert(post);
    }

    @Override
    public int updatePost(Post post) {
        if (post == null || post.getPostId() == null) {
            throw new BusinessException("岗位ID不能为空");
        }
        validatePost(post);
        return baseMapper.updateById(post);
    }

    @Override
    public int deletePosts(List<Long> postIds) {
        if (CollUtil.isEmpty(postIds)) {
            throw new BusinessException("请选择要删除的岗位");
        }
        return baseMapper.deleteBatchIds(postIds);
    }

    @Override
    public boolean checkPostCodeUnique(Post post) {
        if (post == null || StrUtil.isBlank(post.getPostCode())) {
            return true;
        }
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Post::getPostCode, post.getPostCode());
        if (post.getPostId() != null) {
            wrapper.ne(Post::getPostId, post.getPostId());
        }
        return this.count(wrapper) == 0;
    }

    @Override
    public boolean checkPostNameUnique(Post post) {
        if (post == null || StrUtil.isBlank(post.getPostName())) {
            return true;
        }
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Post::getPostName, post.getPostName());
        if (post.getPostId() != null) {
            wrapper.ne(Post::getPostId, post.getPostId());
        }
        return this.count(wrapper) == 0;
    }

    /** 新增/修改时的业务校验 */
    private void validatePost(Post post) {
        if (post == null || StrUtil.isBlank(post.getPostName())) {
            throw new BusinessException("岗位名称不能为空");
        }
        if (!checkPostCodeUnique(post)) {
            throw new BusinessException("岗位编码已存在");
        }
        if (!checkPostNameUnique(post)) {
            throw new BusinessException("岗位名称已存在");
        }
    }

    /** 构造岗位查询条件 */
    private LambdaQueryWrapper<Post> buildQueryWrapper(Post post) {
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        if (post != null) {
            if (StrUtil.isNotBlank(post.getPostCode())) {
                wrapper.like(Post::getPostCode, post.getPostCode());
            }
            if (StrUtil.isNotBlank(post.getPostName())) {
                wrapper.like(Post::getPostName, post.getPostName());
            }
            if (post.getStatus() != null) {
                wrapper.eq(Post::getStatus, post.getStatus());
            }
        }
        wrapper.orderByAsc(Post::getPostSort);
        return wrapper;
    }
}

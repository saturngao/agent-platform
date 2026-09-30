package com.aip.platform.system.controller;

import com.aip.platform.common.PageResult;
import com.aip.platform.common.R;
import com.aip.platform.system.entity.Post;
import com.aip.platform.system.service.ISysPostService;
import com.aip.platform.system.vo.PageParam;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 岗位管理接口
 */
@RestController
@RequestMapping("/api/system/post")
@RequiredArgsConstructor
public class SysPostController {

    private final ISysPostService postService;

    /**
     * 岗位列表（分页；pageNum 或 pageSize 小于等于 0 时全量返回）
     */
    @GetMapping("/list")
    public R<PageResult<Post>> list(Post post, PageParam query) {
        return R.ok(postService.selectPostPage(query, post));
    }

    /**
     * 岗位详情（修改弹窗回显）
     */
    @GetMapping("/{postId}")
    public R<Post> detail(@PathVariable("postId") Long postId) {
        Post post = postService.selectPostById(postId);
        if (post == null) {
            return R.fail("岗位不存在");
        }
        return R.ok(post);
    }

    /**
     * 新增岗位
     */
    @PreAuthorize("@permissionChecker.has('system:post:add')")
    @PostMapping("/add")
    public R<Void> add(@RequestBody Post post) {
        postService.insertPost(post);
        return R.ok();
    }

    /**
     * 修改岗位
     */
    @PreAuthorize("@permissionChecker.has('system:post:edit')")
    @PutMapping("/edit")
    public R<Void> edit(@RequestBody Post post) {
        postService.updatePost(post);
        return R.ok();
    }

    /**
     * 批量删除岗位（逗号分隔）
     */
    @PreAuthorize("@permissionChecker.has('system:post:remove')")
    @DeleteMapping("/remove/{ids}")
    public R<Void> remove(@PathVariable("ids") List<Long> ids) {
        postService.deletePosts(ids);
        return R.ok();
    }
}

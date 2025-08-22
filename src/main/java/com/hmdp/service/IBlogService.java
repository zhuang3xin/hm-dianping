package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {
    /**
     * 根据id查看探店笔记
     * @param id
     * @return
     */
    Result queryBlogById(Long id);

    /**
     * 点赞
     * @param id
     * @return
     */
    Result likeBlog(Long id);

    Result queryHotBlog(Integer current);

    /**
     * 查询点赞列表
     * @param id
     * @return
     */
    Result queryBlogLikes(Long id);
}

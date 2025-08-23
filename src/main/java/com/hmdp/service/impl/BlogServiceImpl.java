package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.ScrollResult;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@RequiredArgsConstructor
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    private final IUserService userService;
    private final StringRedisTemplate stringRedisTemplate;
    private final IFollowService followService;

    /**
     * 根据id查看探店笔记 13686869696
     * @param id
     * @return
     */
    @Override
    public Result queryBlogById(Long id) {
        // 1.从数据库中查询
        Blog blog = getById(id);

        if (blog == null) {
            return Result.fail("笔记不存在！");
        }

        // 2.查询blog有关的用户
        queryBlogUser(blog);

        // 3.查询blog是否被该用户点赞
        isBlogLiked(blog);

        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        // 1.查询用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 用户未登录，无需查询是否点赞
            return;
        }

        Long userId = user.getId();
        String key = BLOG_LIKED_KEY + blog.getId();

        // 2.判断当前登录用户是否已经点赞
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    /**
     * 点赞
     * @param id
     * @return
     */
    @Override
    public Result likeBlog(Long id) {
        // 1.获取登录用户
        Long userId = UserHolder.getUser().getId();

        // 2.判断当前用户是否已经点赞
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        if (score == null) {
            // 3.如果未点赞，可以点赞
            // 3.1.数据库点赞数+1
            boolean isSuccess = lambdaUpdate().setSql("liked = liked + 1")
                    .eq(id != null, Blog::getId, id)
                    .update();

            // 3.2.保存用户到Redis的set集合
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            // 4.如果已点赞，取消点赞
            // 4.1.数据库点赞数-1
            boolean isSuccess = lambdaUpdate().setSql("liked = liked - 1")
                    .eq(id != null, Blog::getId, id)
                    .update();

            // 4.2.把用户从Redis的set集合移除
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }

        return Result.ok();
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });

        return Result.ok(records);
    }

    /**
     * 查询点赞列表
     * @param id
     * @return
     */
    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        // 1.查询top5的点赞用户
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        // 2.解析出其中的用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);

        // 3.根据用户id查询用户
        List<UserDTO> userDTOS = userService.lambdaQuery()
                .in(User::getId, idStr)
                .last("ORDER BY FIELD(id," + idStr + ")")
                .list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 1.获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());

        // 2.保存探店笔记
        boolean isSuccess = save(blog);
        if(!isSuccess){
            return Result.fail("新增笔记失败!");
        }

        // 3.查询笔记作者的所有粉丝 select * from tb_follow where follow_user_id = ?
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();

        // 4.推送笔记id给所有粉丝
        for (Follow follow : follows) {
            // 4.1.获取粉丝id
            Long userId = follow.getUserId();
            // 4.2.推送
            String key = FEED_KEY + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        }

        // 5.返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        // 1.获取用户id
        Long userId = UserHolder.getUser().getId();

        // 2.从收件箱中获取博文
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(FEED_KEY + userId, 0, max, offset, 2);
        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }

        // 3.解析数据，minTime，offset，ids
        long minTime = 0;
        int os = 1;
        ArrayList<Long> ids = new ArrayList<>(typedTuples.size());

        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            ids.add(Long.valueOf(typedTuple.getValue()));
            Long time = typedTuple.getScore().longValue();

            if (time == minTime) {
                os++;
            } else {
                minTime = time;
                os = 1;
            }
        }
        os = minTime == max ? offset + os : os;

        // 4.去数据库查询博文数据
        String strIds = StrUtil.join(",", ids);
        List<Blog> blogList = lambdaQuery()
                .in(Blog::getId, strIds)
                .last("ORDER BY FIELD(id," + strIds + ")")
                .list();

        blogList.forEach(blog -> {
            queryBlogUser(blog);
            isBlogLiked(blog);
        });

        // 5.封装数据
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);
        scrollResult.setList(blogList);

        // 6.返回
        return Result.ok(scrollResult);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}

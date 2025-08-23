package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.USER_FOLLOW_KEY;

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
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    private final StringRedisTemplate stringRedisTemplate;
    private final IUserService userService;

    /**
     * 关注
     * @param followUserId
     * @param isFollow
     * @return
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        // 1.获取用户id
        Long userId = UserHolder.getUser().getId();
        String key = USER_FOLLOW_KEY + userId;

        // 2.判断是取关还是关注操作
        if (isFollow) {
            // 2.1.关注，新增数据
            Follow follow = new Follow();
            follow.setFollowUserId(followUserId);
            follow.setUserId(userId);
            boolean isSuccess = save(follow);

            // 2.2.如果关注成功，则将博主id加入到该用户的关注集合
            if (isSuccess) {
                stringRedisTemplate.opsForSet().add(key, followUserId.toString());
            }
        } else {
            // 2.1.取消关注
            boolean isSuccess = remove(new LambdaQueryWrapper<Follow>().eq(Follow::getFollowUserId, followUserId)
                    .eq(Follow::getUserId, userId));

            // 2.2.如果取关成功，则将博主id从用户的关注集合中抽离
            if (isSuccess) {
                stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
            }
        }

        return Result.ok();
    }

    /**
     * 是否关注
     * @param followUserId
     * @return
     */
    @Override
    public Result isFollow(Long followUserId) {
        // 1.获取用户信息
        UserDTO user = UserHolder.getUser();

        // 2.查询数据库
        Integer count = lambdaQuery().eq(Follow::getUserId, user.getId())
                .eq(Follow::getFollowUserId, followUserId)
                .count();

        return Result.ok(count > 0);
    }

    /**
     * 共同关注
     * @param id
     * @return
     */
    @Override
    public Result commonFollow(Long id) {
        // 1.获取用户id
        Long userId = UserHolder.getUser().getId();

        // 2.通过交集获取共同关注
        Set<String> commonSet = stringRedisTemplate.opsForSet().intersect(USER_FOLLOW_KEY + userId, USER_FOLLOW_KEY + id);
        if (commonSet == null || commonSet.isEmpty()) {
            // 无交集
            return Result.ok();
        }

        // 3.将id抽取出来
        List<Long> ids = commonSet.stream().map(Long::valueOf).collect(Collectors.toList());
        String strIds = StrUtil.join(",", ids);

        // 4.批量查询
        List<UserDTO> userDTOS = userService.lambdaQuery()
                .in(User::getId, strIds)
                .list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDTOS);
    }
}

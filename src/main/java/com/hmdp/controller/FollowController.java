package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/follow")
@RequiredArgsConstructor
public class FollowController {
    private final IFollowService followService;

    /**
     * 关注
     * @param id
     * @param isFollow
     * @return
     */
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long id, @PathVariable("isFollow") Boolean isFollow) {
        log.info("关注：id = {}, isFollow = {}", id, isFollow);
        return followService.follow(id, isFollow);
    }

    /**
     * 是否关注
     * @param id
     * @return
     */
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long id) {
        log.info("是否关注：{}", id);
        return followService.isFollow(id);
    }

    /**
     * 共同关注
     * @param id
     * @return
     */
    @GetMapping("/common/{id}")
    public Result commonFollow(@PathVariable("id") Long id) {
        log.info("共同关注：{}", id);
        return followService.commonFollow(id);
    }
}

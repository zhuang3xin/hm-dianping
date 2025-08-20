package com.hmdp;

import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

@SpringBootTest
public class RedissonTest {
    @Resource
    private RedissonClient redissonClient;

    @Test
    void testRedisson() throws InterruptedException {
        // 获取锁对象
        RLock lock = redissonClient.getLock("anyLock");

        // 获取锁
        boolean isLock = lock.tryLock(1, 10, TimeUnit.SECONDS);

        // 判断是否获取锁成功
        if (isLock) {
            try {
                System.out.println("执行操作");
            } finally {
                lock.unlock();
            }
        }
    }
}

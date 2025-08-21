package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.lock.impl.SimpleRedisLock;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.aop.framework.AopProxy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.concurrent.*;

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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    private final RedissonClient redissonClient;
    private final ISeckillVoucherService seckillVoucherService;
    private final RedisIdWorker redisIdWorker;
    private final StringRedisTemplate stringRedisTemplate;
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    private IVoucherOrderService proxy;

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    //在类初始化之后执行，因为当这个类初始化好了之后，随时都是有可能要执行的
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    // 用于线程池处理的任务
    // 当初始化完毕后，就会去队列中拿消息
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    // 1.获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    // 2.创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }

        private void handleVoucherOrder(VoucherOrder voucherOrder) throws InterruptedException {
            // 1.获取用户
            Long userId = voucherOrder.getUserId();
            // 2.创建锁对象
            RLock redisLock = redissonClient.getLock("lock:order:" + userId);
            // 3.尝试获取锁
            boolean isLock = redisLock.tryLock(1, 10, TimeUnit.SECONDS);
            // 4.判断是否获得锁成功
            if (!isLock) {
                // 获取锁失败，直接返回失败或者重试
                log.error("不允许重复下单！");
                return;
            }
            try {
                // 注意：由于spring的事务是放在threadlocal中，此时的是多线程，事务会失效
                proxy.createVoucherOrder(voucherOrder);
            } finally {
                // 释放锁
                redisLock.unlock();
            }
        }
    }

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Override
    public Result seckillVoucher(Long voucherId) throws InterruptedException {
        // 1.获取用户id、订单id
        Long userId = UserHolder.getUser().getId();
        Long orderId = redisIdWorker.nextId("order");

        // 2.执行lua脚本
        Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(),
                voucherId.toString(), userId.toString(), String.valueOf(orderId));

        int r = result.intValue();

        // 3.r不为0，报错
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "不能重复下单");
        }

        // 4.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setUserId(userId);
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);

        // 5.放入阻塞队列
        orderTasks.add(voucherOrder);
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        // 6.返回订单id
        return Result.ok(orderId);
    }

    @Override
    @Transactional
    public Result createVoucherOrder(VoucherOrder voucherOrder) {
        // 4.用户是否已经买过这个优惠价
        // 4.1.获取用户id、优惠卷id
        Long userId = UserHolder.getUser().getId();
        Long voucherId = voucherOrder.getVoucherId();

        // 4.2.获取订单
        Integer count = lambdaQuery().eq(VoucherOrder::getVoucherId, voucherId)
                .eq(VoucherOrder::getUserId, userId)
                .count();
        // 4.3.判断用户是否买过
        if (count > 0) {
            // 4.3.1.买过则直接返回
            return Result.fail("用户已经购买过一次！");
        }

        // 4.3.2.没买过则扣减库存
        // 5.扣减库存
        boolean isSuccess = seckillVoucherService.lambdaUpdate()
                .setSql("stock = stock - 1")
                .eq(SeckillVoucher::getVoucherId, voucherId)
                .gt(SeckillVoucher::getStock, 0)
                .update();

        if (!isSuccess) {
            return Result.fail("库存不足！");
        }

        save(voucherOrder);

        return Result.ok(voucherOrder.getId());
    }
}

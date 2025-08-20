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
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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

    private final ISeckillVoucherService seckillVoucherService;
    private final RedisIdWorker redisIdWorker;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1.去数据库查询秒杀卷
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        LocalDateTime now = LocalDateTime.now();

        // 2.判断是否在规定时间之内
        if (now.isBefore(seckillVoucher.getBeginTime())) {
            return Result.fail("秒杀尚未开始！");
        }
        if (now.isAfter(seckillVoucher.getEndTime())) {
            return Result.fail("秒杀已经结束！");
        }

        // 3.判断库存是否充足
        if (seckillVoucher.getStock() < 1) {
            return Result.fail("库存不足！");
        }

        Long userId = UserHolder.getUser().getId();
        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
        // 4.获取锁
        boolean isLock = lock.tryLock(1200L);
        // 5.判断是否获取到锁
        // 5.1.加锁失败
        if (!isLock) {
            return Result.fail("不允许重复下单");
        }

        // 5.2.获取到锁，创建订单
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            lock.unLock();
        }
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 4.用户是否已经买过这个优惠价
        // 4.1.获取用户
        UserDTO user = UserHolder.getUser();

        // 4.2.获取订单
        Integer count = lambdaQuery().eq(VoucherOrder::getVoucherId, voucherId)
                .eq(VoucherOrder::getUserId, user.getId())
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

        // 6.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 6.1.订单id
        Long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        // 6.2.用户id
        voucherOrder.setUserId(user.getId());
        // 6.3.代金卷id
        voucherOrder.setVoucherId(voucherId);

        return Result.ok(orderId);
    }
}

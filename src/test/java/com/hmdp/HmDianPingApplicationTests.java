package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private IShopService shopService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                Long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };

        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {

        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }

    @Test
    void testShopGeo() {
        // 1.获取商户列表
        List<Shop> shopList = shopService.list();

        // 2.根据typeId分组
        Map<Long, List<Shop>> map = shopList.stream().collect(Collectors.groupingBy(Shop::getTypeId));

        // 3.分批完成写入Redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            // 3.1.获取key
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;

            // 3.2.获取同类型店铺的集合
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> geoLocations = new ArrayList<>(value.size());
            for (Shop shop : value) {
                geoLocations.add(new RedisGeoCommands.GeoLocation(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())));
            }

            // 3.3.写入redis
            stringRedisTemplate.opsForGeo().add(key, geoLocations);
        }
    }

    @Test
    void testHyperLogLog() {
        // 准备数组，存储用户数据
        String[] users = new String[1000];

        // 数组角标
        int index = 0;

        // 存储
        for (int i = 1; i <= 1000000; i++) {
            users[index++] = "user_" + i;

            // 每一千条数据上传一次
            if (i % 1000 == 0) {
                index = 0;
                stringRedisTemplate.opsForHyperLogLog().add("hll1", users);
            }
        }

        // 统计数量
        Long size = stringRedisTemplate.opsForHyperLogLog().size("hll1");
        System.out.println("size = " + size);
    }
}

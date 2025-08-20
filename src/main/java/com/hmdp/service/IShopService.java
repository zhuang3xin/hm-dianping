package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

import java.lang.reflect.InvocationTargetException;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {
    /**
     * 根据id查询商铺信息
     * @param id
     * @return
     */
    Result queryById(Long id) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException;

    /**
     * 更新商铺信息
     *
     * @param shop
     * @return
     */
    Result updateShopById(Shop shop);
}

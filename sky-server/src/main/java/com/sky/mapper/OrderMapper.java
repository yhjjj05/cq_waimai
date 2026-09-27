package com.sky.mapper;

import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderMapper {

    /**
     * 向订单表插入1条数据
     * @param orders
     */
    void insert(Orders orders);


    /**
     * 根据订单号查询订单
     * @param orderNumber
     * @return
     */
    Orders getByNumber(String orderNumber);


    /**
     * 动态更新订单信息，只更新 orders 中非 null 的字段
     * @param orders
     */
    void update(Orders orders);
}

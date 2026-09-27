package com.sky.constant;

/**
 * 店铺相关常量
 */
public class ShopConstant {

    /**
     * 店铺营业状态在 redis 中的 key
     */
    public static final String SHOP_STATUS_KEY = "SHOP_STATUS";

    /**
     * 营业中
     */
    public static final Integer STATUS_OPEN = 1;

    /**
     * 打烊中
     */
    public static final Integer STATUS_CLOSED = 0;
}

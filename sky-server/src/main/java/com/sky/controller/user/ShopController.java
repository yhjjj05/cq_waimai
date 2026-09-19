package com.sky.controller.user;

import com.sky.constant.ShopConstant;
import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController("userShopController")
@RequestMapping("/user/shop")
@Api(tags = "店铺相关接口")
@Slf4j
public class ShopController {

    @Autowired
    private RedisTemplate redisTemplate;


    /**
     * 获取店铺营业状态
     * @return
     */
    @GetMapping("/status")
    @ApiOperation("获取店铺营业状态")
    public Result<Integer> getStatus(){
        Integer status = (Integer) redisTemplate.opsForValue().get(ShopConstant.SHOP_STATUS_KEY);

        // 从未设置过营业状态时，默认按打烊处理，避免返回 null
        if (status == null) {
            status = ShopConstant.STATUS_CLOSED;
        }

        log.info("获取店铺营业状态：{}",status);

        return Result.success(status);
    }


}




















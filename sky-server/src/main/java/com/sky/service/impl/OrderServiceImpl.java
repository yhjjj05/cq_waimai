package com.sky.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private AddressBookMapper addressBookMapper;

    @Autowired
    private ShoppingCartMapper shoppingCartMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private WeChatPayUtil weChatPayUtil;


    /**
     * 用户下单
     *
     * @param ordersSubmitDTO
     * @return
     */
    @Override
    @Transactional
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {
        // 处理业务异常
        // 地址为空
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }

        // 购物车为空
        Long userId = BaseContext.getCurrentId();

        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);

        if (shoppingCartList == null || shoppingCartList.isEmpty()) {
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }


        // 向订单表插入1条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setOrderTime(LocalDateTime.now());
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setPayStatus(Orders.UN_PAID);
        orders.setUserId(userId);
        orders.setPhone(addressBook.getPhone());
        orders.setConsignee(addressBook.getConsignee());

        orderMapper.insert(orders);

        Long orderId = orders.getId();

        // 向订单明细表插入n条数据
        List<OrderDetail> orderDetailList = new ArrayList<>();

        for (ShoppingCart cart : shoppingCartList) {
            OrderDetail orderDetail = new OrderDetail();
            BeanUtils.copyProperties(cart, orderDetail);

            orderDetail.setOrderId(orderId);

            orderDetailList.add(orderDetail);
        }

        orderDetailMapper.insertBatch(orderDetailList);

        // 清空购物车
        shoppingCartMapper.deleteByUserId(userId);

        // 封装VO返回结果
        return OrderSubmitVO.builder()
                .id(orderId)
                .orderTime(orders.getOrderTime())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .build();
    }


    /**
     * 本地 mock 支付开关：
     * true  —— 不调微信支付，直接走本地"支付成功"逻辑，订单状态推进到"待接单(2)"
     * false —— 调真实微信支付接口（需要 application-dev.yml 里补全 sky.wechat 下的
     * 商户号、商户API私钥、平台证书、回调地址等配置，否则会 NPE）
     */
    private static final boolean MOCK_PAY = true;

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    @Override
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);
        if (user == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        if (MOCK_PAY) {
            // 本地模拟支付成功。
            // 真实流程里这一步是微信服务器异步回调 /notify/paySuccess 触发的，这里直接同步做掉，
            // 目的只是让订单状态推进到"待接单"，后续管理端接单/派送/完成、统计等功能才拿得到数据。
            paySuccess(ordersPaymentDTO.getOrderNumber());

            // 返回一个占位 VO，让前端"去支付"能正常走完（小程序端不校验这些字段的真实性）
            return OrderPaymentVO.builder()
                    .timeStamp(String.valueOf(System.currentTimeMillis() / 1000))
                    .nonceStr(UUID.randomUUID().toString().replace("-", ""))
                    .packageStr("prepay_id=mock_" + ordersPaymentDTO.getOrderNumber())
                    .signType("RSA")
                    .paySign("mock_sign")
                    .build();
        }

        //调用微信支付接口，生成预支付交易单
        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                new BigDecimal(0.01), //支付金额，单位 元
                "苍穹外卖订单", //商品描述
                user.getOpenid() //微信用户的openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     * <p>
     * 注：目前由 payment() 同类内直接调用，属于自调用，Spring 代理不生效，@Transactional 实际不起作用
     * （只有一条 update，本来就是原子的，无影响）。等以后接真实微信支付、由回调 Controller 通过
     * OrderService 接口调用时，这个注解才会真正生效 —— 那时记得把本方法声明进 OrderService 接口。
     *
     * @param outTradeNo
     */
    @Transactional
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumber(outTradeNo);
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 幂等 + 状态校验：只有"待付款"的订单才能推进到"待接单"。
        // 微信回调会重复推送，且一个迟到的回调不能把已取消/已完成的订单改回"待接单"。
        if (!Orders.PENDING_PAYMENT.equals(ordersDB.getStatus())) {
            return;
        }

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);
    }


    /**
     * 历史订单查询
     * @param ordersPageQueryDTO
     * @return
     */
    @Override
    public PageResult pageQuery(OrdersPageQueryDTO ordersPageQueryDTO) {
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        // 查询到的每一页是一个list，由多个订单构成，每个订单还有明细
        List<OrderVO> list = new ArrayList();

        // 查询出订单明细，并封装入OrderVO进行响应
        if (page != null && page.getTotal() > 0) {
            for (Orders orders : page) {
                Long orderId = orders.getId();

                // 查询订单明细
                List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(orderId);

                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(orderDetails);

                list.add(orderVO);
            }
        }

        return new PageResult(page.getTotal(), list);
    }


    /**
     * 查询订单详情
     * @param id
     * @return
     */
    @Override
    public OrderVO details(Long id) {
        // 根据id查询订单
        Orders orders = orderMapper.getById(id);

        // 根据订单id查询明细
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        //封装成VO
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailList);

        return orderVO;
    }


    /**
     * 用户取消订单
     * @param id
     */
    @Override
    public void userCancelById(Long id) throws Exception {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(id);

        // 订单是否存在
        if(ordersDB == null){
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 订单状态
        if (ordersDB.getStatus() > 2){
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }

        Orders orders = new Orders();
        orders.setId(ordersDB.getId());

        // 待接单状态说明用户已支付，取消前必须先退款；待付款状态没付过钱，直接取消
        if (Orders.TO_BE_CONFIRMED.equals(ordersDB.getStatus())) {
            // 调用微信支付退款接口。
            // 金额必须与 payment() 里实付的一致（微信沙箱是 0.01），
            // 填 ordersDB.getAmount() 会因"退款额超过实付额"被微信拒绝。
            weChatPayUtil.refund(ordersDB.getNumber(), ordersDB.getNumber(),
                    new BigDecimal(0.01), new BigDecimal(0.01));

            // 支付状态修改为已退款
            orders.setPayStatus(Orders.REFUND);
        }

        // 更新订单状态、取消原因、取消时间
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason("用户取消");
        orders.setCancelTime(LocalDateTime.now());

        orderMapper.update(orders);
    }


    /**
     * 再来一单
     * @param id
     */
    @Override
    @Transactional
    public void repetition(Long id) {
        // 查询当前用户id(shopping_cart需要)
        Long userId = BaseContext.getCurrentId();

        // 校验订单存在且属于当前用户，防止改 URL 里的 id 把别人的单加进自己购物车
        Orders ordersDB = orderMapper.getById(id);
        if (ordersDB == null || !userId.equals(ordersDB.getUserId())) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        // 根据订单id查询当前订单详情
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

        // 将其添加到购物车
        for (OrderDetail orderDetail : orderDetailList) {
            ShoppingCart shoppingCart = new ShoppingCart();

            // 复制菜品信息；id 必须排除——那是 order_detail 的主键，购物车主键由数据库自增
            BeanUtils.copyProperties(orderDetail, shoppingCart, "id");
            shoppingCart.setUserId(userId);

            // 购物车同一商品只保留一行、数量累加，与 addShoppingCart 的行为保持一致
            List<ShoppingCart> existList = shoppingCartMapper.list(shoppingCart);

            if (existList != null && !existList.isEmpty()) {
                ShoppingCart cart = existList.get(0);
                cart.setNumber(cart.getNumber() + orderDetail.getNumber());

                shoppingCartMapper.updateNumberById(cart);
            } else {
                shoppingCart.setCreateTime(LocalDateTime.now());

                shoppingCartMapper.insert(shoppingCart);
            }
        }
    }

    //
}





















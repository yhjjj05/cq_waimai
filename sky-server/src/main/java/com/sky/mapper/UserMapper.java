package com.sky.mapper;

import com.sky.entity.User;
import io.swagger.models.auth.In;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.HashMap;

@Mapper
public interface UserMapper {

    /**
     * 根据openid查询用户
     * @param openid
     * @return
     */
    @Select("select * from user where openid = #{openid}")
    User getByOpenid(String openid);


    /**
     * 根据主键查询用户
     * @param id
     * @return
     */
    @Select("select * from user where id = #{id}")
    User getById(Long id);


    /**
     * 插入数据
     * @param user
     */
    void insert(User user);


    /**
     * 查询具体某一天的用户数量
     * @param map
     * @return
     */
    Integer countByMap(HashMap map);
}









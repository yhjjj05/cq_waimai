package com.sky.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置类：自定义一个 RedisTemplate 并交给 Spring 容器管理
 * <p>
 * 不配置该类时，Spring Boot 会自动装配一个 RedisTemplate，
 * 但它默认使用 JDK 序列化（JdkSerializationRedisSerializer），
 * 存到 redis 里的 key 会带上一串乱码前缀，用 redis 客户端查看很不直观。
 * 这里手动指定 key 使用字符串序列化，让 redis 中的 key 以明文可读的形式保存。
 */
@Configuration   // 声明为配置类，Spring 启动时会加载它
@Slf4j           // Lombok 注解，自动生成 log 日志对象
public class RedisConfiguration {

    /**
     * 创建 RedisTemplate 对象并放入 Spring 容器
     * 方法名 redisTemplate 即为该 Bean 的名称，其他地方可直接 @Autowired 注入
     *
     * @param redisConnectionFactory 连接工厂，由 Spring Boot 根据 application.yml 中的
     *                               spring.redis.* 配置自动创建，这里通过方法参数注入
     */
    @Bean
    public RedisTemplate redisTemplate(RedisConnectionFactory redisConnectionFactory){
        log.info("开始创建redis模版对象. . .");

        RedisTemplate redisTemplate = new RedisTemplate();

        // 设置redis连接工厂对象
        // 没有连接工厂，RedisTemplate 无法与 redis 服务器建立连接
        redisTemplate.setConnectionFactory(redisConnectionFactory);

        // 设置redis key序列化器
        // 指定 key 使用 StringRedisSerializer，redis 中的 key 就是正常的字符串
        // 注意：这里只设置了 key 的序列化器，value 仍使用默认的 JDK 序列化
        redisTemplate.setKeySerializer(new StringRedisSerializer());

        return redisTemplate;
    }

}













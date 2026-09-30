package com.aip.platform.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.aip.platform.common.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 配置：分页插件 + 自动填充审计字段
 */
@Slf4j
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        // 防止全表更新/删除
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        return interceptor;
    }

    /**
     * 审计字段自动填充（createBy / createTime / updateBy / updateTime）
     */
    @Bean
    public MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                try {
                    LocalDateTime now = LocalDateTime.now();
                    this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
                    this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
                    String username = SecurityUtils.getUsername();
                    if (username != null) {
                        this.strictInsertFill(metaObject, "createBy", String.class, username);
                        this.strictInsertFill(metaObject, "updateBy", String.class, username);
                    }
                } catch (Exception ignored) {
                    // 字段不存在时静默跳过
                }
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                try {
                    this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
                    String username = SecurityUtils.getUsername();
                    if (username != null) {
                        this.strictUpdateFill(metaObject, "updateBy", String.class, username);
                    }
                } catch (Exception ignored) {
                    // 字段不存在时静默跳过
                }
            }
        };
    }
}

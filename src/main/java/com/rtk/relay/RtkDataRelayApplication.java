package com.rtk.relay;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * RTK数据转发服务启动类
 * 
 * @author RTK Team
 * @version 1.0.0
 */
@SpringBootApplication(exclude = {
    org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class,
    org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class,
    // 排除数据库相关的自动配置，允许无数据库启动
    DataSourceAutoConfiguration.class,
    DataSourceTransactionManagerAutoConfiguration.class,
    HibernateJpaAutoConfiguration.class
})
@EnableScheduling
@Slf4j
public class RtkDataRelayApplication {

    public static void main(String[] args) {
        try {
            SpringApplication.run(RtkDataRelayApplication.class, args);
            log.info("RTK数据转发服务启动成功！");
        } catch (Exception e) {
            log.error("RTK数据转发服务启动失败：", e);
            System.exit(1);
        }
    }
}

package com.dorm.repair;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 学生宿舍报修管理系统 —— 启动类。
 * <p>
 * 运行 main 方法后访问 http://localhost:8080 。
 * {@code @MapperScan} 让 MyBatis 自动扫描 mapper 包下的接口并生成实现。
 */
@SpringBootApplication
@MapperScan("com.dorm.repair.mapper")
public class DormRepairApplication {

    public static void main(String[] args) {
        SpringApplication.run(DormRepairApplication.class, args);
    }
}

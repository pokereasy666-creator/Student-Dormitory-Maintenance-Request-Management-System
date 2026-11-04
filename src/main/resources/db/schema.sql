-- =====================================================================
-- 学生宿舍报修管理系统 建表脚本（MySQL 8）
-- 应用启动时自动执行；使用 IF NOT EXISTS，已有表和数据不会被删除。
-- 也可以手动在 MySQL 客户端中执行：先 CREATE DATABASE dorm_repair DEFAULT CHARSET utf8mb4; USE dorm_repair;
-- 表显式指定 utf8mb4 字符集：即使数据库默认是 latin1（如 MySQL 5.7），中文也能正常保存。
-- =====================================================================

-- 用户表：学生和维修人员共用，role 区分角色
CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(32)  NOT NULL COMMENT '登录账号：学生为学号，维修人员为工号',
    password    VARCHAR(100) NOT NULL COMMENT 'BCrypt 加密后的密码',
    real_name   VARCHAR(32)  NOT NULL COMMENT '姓名',
    role        VARCHAR(16)  NOT NULL COMMENT '角色：STUDENT 学生 / REPAIRER 维修人员',
    phone       VARCHAR(11)  NOT NULL COMMENT '手机号',
    building    VARCHAR(32)  NULL     COMMENT '楼栋（仅学生）',
    room_no     VARCHAR(16)  NULL     COMMENT '宿舍号（仅学生）',
    created_at  DATETIME     NOT NULL COMMENT '注册时间',
    updated_at  DATETIME     NOT NULL COMMENT '最后修改时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表';

-- 报修工单表：评价与工单一对一，直接存放在本表
CREATE TABLE IF NOT EXISTS repair_order (
    id           BIGINT       NOT NULL AUTO_INCREMENT COMMENT '工单号',
    student_id   BIGINT       NOT NULL COMMENT '报修学生 ID',
    building     VARCHAR(32)  NOT NULL COMMENT '楼栋',
    room_no      VARCHAR(16)  NOT NULL COMMENT '宿舍号',
    category     VARCHAR(20)  NOT NULL COMMENT '报修类型：WATER_ELECTRIC 水电 / DOOR_WINDOW 门窗 / FURNITURE 家具 / BATHROOM 卫生间',
    description  VARCHAR(500) NOT NULL COMMENT '故障描述',
    image_path   VARCHAR(255) NULL     COMMENT '故障图片相对路径，可空',
    status       VARCHAR(20)  NOT NULL COMMENT '状态：PENDING 待接单 / IN_PROGRESS 维修中 / COMPLETED 已完成',
    repairer_id  BIGINT       NULL     COMMENT '接单维修人员 ID',
    repair_note  VARCHAR(500) NULL     COMMENT '维修备注',
    rating       TINYINT      NULL     COMMENT '评价星级 1~5',
    evaluation   VARCHAR(200) NULL     COMMENT '评价内容',
    created_at   DATETIME     NOT NULL COMMENT '提交时间',
    accepted_at  DATETIME     NULL     COMMENT '接单时间',
    completed_at DATETIME     NULL     COMMENT '完成时间',
    rated_at     DATETIME     NULL     COMMENT '评价时间',
    PRIMARY KEY (id),
    KEY idx_order_status (status),
    KEY idx_order_student (student_id),
    KEY idx_order_repairer (repairer_id),
    CONSTRAINT fk_order_student FOREIGN KEY (student_id) REFERENCES sys_user (id),
    CONSTRAINT fk_order_repairer FOREIGN KEY (repairer_id) REFERENCES sys_user (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '报修工单表';

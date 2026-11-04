-- =====================================================================
-- 预置数据：维修人员账号不开放注册，在这里直接录入。
-- INSERT IGNORE：账号已存在时跳过，因此重启应用不会重置已修改过的密码。
-- 下面所有账号的初始密码均为 123456（BCrypt 加密后的值）。
-- 新增维修人员：复制一行，修改工号、姓名、手机号即可。
-- =====================================================================
INSERT IGNORE INTO sys_user (username, password, real_name, role, phone, building, room_no, created_at, updated_at) VALUES
('W001', '$2a$10$OUKmmtLnyHuhSDFhW/iQY.WfEiLoXNX2VuX3HCUfYyF.zvMPn5Gpm', '张师傅', 'REPAIRER', '13800000001', NULL, NULL, NOW(), NOW()),
('W002', '$2a$10$OUKmmtLnyHuhSDFhW/iQY.WfEiLoXNX2VuX3HCUfYyF.zvMPn5Gpm', '李师傅', 'REPAIRER', '13800000002', NULL, NULL, NOW(), NOW()),
('W003', '$2a$10$OUKmmtLnyHuhSDFhW/iQY.WfEiLoXNX2VuX3HCUfYyF.zvMPn5Gpm', '王师傅', 'REPAIRER', '13800000003', NULL, NULL, NOW(), NOW());

-- 演示用学生账号（学号 2023001 / 密码 123456），答辩演示时可直接登录；不需要可删除此行
INSERT IGNORE INTO sys_user (username, password, real_name, role, phone, building, room_no, created_at, updated_at) VALUES
('2023001', '$2a$10$OUKmmtLnyHuhSDFhW/iQY.WfEiLoXNX2VuX3HCUfYyF.zvMPn5Gpm', '演示学生', 'STUDENT', '13900000001', '1号楼', '101', NOW(), NOW());

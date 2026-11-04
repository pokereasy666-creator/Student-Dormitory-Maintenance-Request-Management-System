package com.dorm.repair;

import com.dorm.repair.entity.RepairOrder;
import com.dorm.repair.entity.User;
import com.dorm.repair.enums.OrderStatus;
import com.dorm.repair.enums.Role;
import com.dorm.repair.mapper.RepairOrderMapper;
import com.dorm.repair.mapper.UserMapper;
import com.dorm.repair.service.FileStorageService;
import com.dorm.repair.service.RepairOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 集成测试：启动完整的 Spring 容器 + H2 内存库，通过 MockMvc 模拟浏览器请求，
 * 覆盖完整业务闭环、角色隔离、越权访问、重复接单 / 重复评价等关键规则。
 * <p>
 * 各测试共用同一个内存库，所以每个测试使用不同的学号，互不干扰。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DormRepairApplicationTests {

    /** 预置维修人员（data.sql）的初始密码 */
    private static final String REPAIRER_PWD = "123456";

    /** 一个最小的"PNG 文件"：8 字节 PNG 文件头 + 若干字节，足以通过文件头校验 */
    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R'};

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private RepairOrderMapper orderMapper;
    @Autowired
    private RepairOrderService orderService;
    @Autowired
    private FileStorageService fileStorageService;

    // ============================ 测试用例 ============================

    @Test
    void fullWorkflow_submitAcceptCompleteRate() throws Exception {
        registerStudent("S1001", "abc123");
        MockHttpSession stu = login("S1001", "abc123");

        // 学生首页能看到公告
        mvc.perform(get("/student/home").session(stu))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("报修须知")));

        // 1. 学生提交报修（带图片）
        MockMultipartFile image = new MockMultipartFile("image", "leak.png", "image/png", PNG_BYTES);
        Long id = submitOrder(stu, image, "卫生间水龙头一直滴水");
        RepairOrder order = orderMapper.findById(id);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getImagePath()).isNotNull();
        assertThat(Files.exists(fileStorageService.getRootDir().resolve(order.getImagePath()))).isTrue();

        mvc.perform(get("/student/orders/" + id).session(stu))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("待接单")));

        // 图片：登录后可访问，未登录被拦截
        mvc.perform(get("/uploads/" + order.getImagePath()).session(stu)).andExpect(status().isOk());
        mvc.perform(get("/uploads/" + order.getImagePath())).andExpect(redirectedUrl("/login"));

        // 2. 维修人员在待接单池中看到该工单
        MockHttpSession w1 = login("W001", REPAIRER_PWD);
        MockHttpSession w2 = login("W002", REPAIRER_PWD);
        mvc.perform(get("/repair/pending").session(w1))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("#" + id)));

        // 未完成时学生不能评价
        mvc.perform(post("/student/orders/" + id + "/rate").session(stu).param("rating", "5"))
                .andExpect(flash().attribute("error", containsString("维修完成后才能评价")));

        // 3. W001 接单成功；W002 再接同一单失败
        mvc.perform(post("/repair/orders/" + id + "/accept").session(w1))
                .andExpect(redirectedUrl("/repair/orders/" + id))
                .andExpect(flash().attribute("success", containsString("接单成功")));
        mvc.perform(post("/repair/orders/" + id + "/accept").session(w2))
                .andExpect(redirectedUrl("/repair/pending"))
                .andExpect(flash().attribute("error", containsString("已被其他维修人员接单")));

        // 已被 W001 接走的单，W002 看不到详情，也不能完成
        mvc.perform(get("/repair/orders/" + id).session(w2)).andExpect(status().isBadRequest());
        mvc.perform(post("/repair/orders/" + id + "/complete").session(w2).param("repairNote", "x"))
                .andExpect(flash().attribute("error", containsString("只能完成自己接单")));

        // 4. W001 完成维修：备注必填
        mvc.perform(post("/repair/orders/" + id + "/complete").session(w1).param("repairNote", " "))
                .andExpect(flash().attribute("error", "请填写维修备注"));
        mvc.perform(post("/repair/orders/" + id + "/complete").session(w1).param("repairNote", "更换阀芯"))
                .andExpect(flash().attribute("success", containsString("已标记为完成")));

        // 5. 学生评价，且只能评价一次
        mvc.perform(post("/student/orders/" + id + "/rate").session(stu)
                        .param("rating", "5").param("evaluation", "师傅很快就修好了"))
                .andExpect(flash().attribute("success", containsString("评价成功")));
        mvc.perform(post("/student/orders/" + id + "/rate").session(stu).param("rating", "1"))
                .andExpect(flash().attribute("error", containsString("不能重复评价")));

        RepairOrder done = orderMapper.findById(id);
        assertThat(done.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(done.getRepairerId()).isEqualTo(userMapper.findByUsername("W001").getId());
        assertThat(done.getRepairNote()).isEqualTo("更换阀芯");
        assertThat(done.getRating()).isEqualTo(5);
        assertThat(done.getEvaluation()).isEqualTo("师傅很快就修好了");

        // 维修人员历史记录中能看到这单
        mvc.perform(get("/repair/mine").session(w1).param("status", "COMPLETED"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("#" + id)));
        // 学生按状态筛选
        mvc.perform(get("/student/orders").session(stu).param("status", "COMPLETED"))
                .andExpect(content().string(containsString("#" + id)));
        mvc.perform(get("/student/orders").session(stu).param("status", "PENDING"))
                .andExpect(content().string(not(containsString("#" + id))));
    }

    @Test
    void roleIsolation_enforcedOnServerSide() throws Exception {
        // 未登录：一律跳转登录页
        mvc.perform(get("/student/home")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/repair/pending")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/profile")).andExpect(redirectedUrl("/login"));

        registerStudent("S2001", "abc123");
        MockHttpSession stu = login("S2001", "abc123");
        MockHttpSession w1 = login("W001", REPAIRER_PWD);

        // 学生不能访问维修人员功能（看不到工单池、不能接单）
        mvc.perform(get("/repair/pending").session(stu)).andExpect(status().isForbidden());
        mvc.perform(post("/repair/orders/1/accept").session(stu)).andExpect(status().isForbidden());

        // 维修人员不能提交报修
        mvc.perform(get("/student/orders/new").session(w1)).andExpect(status().isForbidden());
        mvc.perform(multipart("/student/orders").session(w1)
                        .param("building", "1号楼").param("roomNo", "101")
                        .param("category", "FURNITURE").param("description", "伪造报修"))
                .andExpect(status().isForbidden());

        // 登录后访问根路径，按角色进入各自首页
        mvc.perform(get("/").session(stu)).andExpect(redirectedUrl("/student/home"));
        mvc.perform(get("/").session(w1)).andExpect(redirectedUrl("/repair/pending"));
    }

    @Test
    void student_cannotViewOrRateOthersOrder() throws Exception {
        registerStudent("S3001", "abc123");
        registerStudent("S3002", "abc123");
        MockHttpSession a = login("S3001", "abc123");
        MockHttpSession b = login("S3002", "abc123");
        Long id = submitOrder(a, null, "A 同学的私密描述");

        mvc.perform(get("/student/orders/" + id).session(b))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(not(containsString("A 同学的私密描述"))));
        mvc.perform(get("/student/orders").session(b))
                .andExpect(content().string(not(containsString("#" + id))));
        mvc.perform(post("/student/orders/" + id + "/rate").session(b).param("rating", "1"))
                .andExpect(flash().attribute("error", containsString("无权查看")));
    }

    @Test
    void register_validationAndRoleCannotBeForged() throws Exception {
        // 伪造 role=REPAIRER 参数：注册出来仍然是学生
        mvc.perform(post("/register")
                        .param("username", "S4001").param("realName", "测试")
                        .param("password", "abc123").param("confirmPassword", "abc123")
                        .param("building", "2号楼").param("roomNo", "202").param("phone", "13912345678")
                        .param("role", "REPAIRER"))
                .andExpect(redirectedUrl("/login"));
        User user = userMapper.findByUsername("S4001");
        assertThat(user.getRole()).isEqualTo(Role.STUDENT);
        // 密码以 BCrypt 密文保存
        assertThat(user.getPassword()).startsWith("$2a$").isNotEqualTo("abc123");

        // 学号重复
        mvc.perform(post("/register")
                        .param("username", "S4001").param("realName", "测试")
                        .param("password", "abc123").param("confirmPassword", "abc123")
                        .param("building", "2号楼").param("roomNo", "202").param("phone", "13912345678"))
                .andExpect(view().name("register"))
                .andExpect(model().attribute("error", containsString("已注册")));

        // 手机号格式错误
        mvc.perform(post("/register")
                        .param("username", "S4002").param("realName", "测试")
                        .param("password", "abc123").param("confirmPassword", "abc123")
                        .param("building", "2号楼").param("roomNo", "202").param("phone", "12345"))
                .andExpect(view().name("register"))
                .andExpect(model().attributeHasFieldErrors("registerForm", "phone"));

        // 两次密码不一致
        mvc.perform(post("/register")
                        .param("username", "S4003").param("realName", "测试")
                        .param("password", "abc123").param("confirmPassword", "abc999")
                        .param("building", "2号楼").param("roomNo", "202").param("phone", "13912345678"))
                .andExpect(model().attribute("error", "两次输入的密码不一致"));

        // 不存在的楼栋
        mvc.perform(post("/register")
                        .param("username", "S4004").param("realName", "测试")
                        .param("password", "abc123").param("confirmPassword", "abc123")
                        .param("building", "99号楼").param("roomNo", "202").param("phone", "13912345678"))
                .andExpect(model().attribute("error", "请选择正确的楼栋"));
        assertThat(userMapper.findByUsername("S4004")).isNull();
    }

    @Test
    void login_wrongPasswordRejected() throws Exception {
        mvc.perform(post("/login").param("username", "W001").param("password", "wrong"))
                .andExpect(view().name("login"))
                .andExpect(model().attribute("error", "账号或密码错误"));
        mvc.perform(post("/login").param("username", "nobody").param("password", "whatever"))
                .andExpect(model().attribute("error", "账号或密码错误"));
    }

    @Test
    void upload_rejectsNonImageFile() throws Exception {
        registerStudent("S5001", "abc123");
        MockHttpSession stu = login("S5001", "abc123");
        int before = orderMapper.listByStudent(userMapper.findByUsername("S5001").getId(), null).size();

        // 扩展名是 .jpg，但内容其实是文本
        MockMultipartFile fake = new MockMultipartFile("image", "fake.jpg", "image/jpeg",
                "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/student/orders").file(fake).session(stu)
                        .param("building", "1号楼").param("roomNo", "101")
                        .param("category", "DOOR_WINDOW").param("description", "窗户关不上"))
                .andExpect(view().name("student/order-form"))
                .andExpect(model().attribute("error", "文件内容不是有效的图片"));

        // 不允许的扩展名
        MockMultipartFile exe = new MockMultipartFile("image", "a.exe", "application/octet-stream", PNG_BYTES);
        mvc.perform(multipart("/student/orders").file(exe).session(stu)
                        .param("building", "1号楼").param("roomNo", "101")
                        .param("category", "DOOR_WINDOW").param("description", "窗户关不上"))
                .andExpect(model().attribute("error", containsString("仅支持")));

        int after = orderMapper.listByStudent(userMapper.findByUsername("S5001").getId(), null).size();
        assertThat(after).isEqualTo(before);
    }

    @Test
    void submitOrder_validation() throws Exception {
        registerStudent("S5101", "abc123");
        MockHttpSession stu = login("S5101", "abc123");
        // 不存在的报修类型 + 空描述
        mvc.perform(multipart("/student/orders").session(stu)
                        .param("building", "1号楼").param("roomNo", "101")
                        .param("category", "NOT_A_CATEGORY").param("description", ""))
                .andExpect(view().name("student/order-form"))
                .andExpect(model().attributeHasFieldErrors("orderForm", "category", "description"))
                .andExpect(content().string(containsString("请选择正确的报修类型")));
    }

    @Test
    void changePassword_requiresOldPasswordAndForcesRelogin() throws Exception {
        registerStudent("S6001", "abc123");
        MockHttpSession stu = login("S6001", "abc123");

        mvc.perform(post("/profile/password").session(stu)
                        .param("oldPassword", "wrong1").param("newPassword", "new123").param("confirmPassword", "new123"))
                .andExpect(model().attribute("error", "原密码错误"));

        mvc.perform(post("/profile/password").session(stu)
                        .param("oldPassword", "abc123").param("newPassword", "new123").param("confirmPassword", "new123"))
                .andExpect(redirectedUrl("/login"))
                .andExpect(flash().attribute("success", containsString("重新登录")));
        assertThat(stu.isInvalid()).isTrue();

        mvc.perform(post("/login").param("username", "S6001").param("password", "abc123"))
                .andExpect(model().attribute("error", "账号或密码错误"));
        login("S6001", "new123");
    }

    @Test
    void updateProfile_updatesDbAndSession() throws Exception {
        registerStudent("S7001", "abc123");
        MockHttpSession stu = login("S7001", "abc123");

        mvc.perform(post("/profile").session(stu)
                        .param("realName", "新名字").param("phone", "13700000000")
                        .param("building", "3号楼").param("roomNo", "303"))
                .andExpect(redirectedUrl("/profile"));

        User user = userMapper.findByUsername("S7001");
        assertThat(user.getRealName()).isEqualTo("新名字");
        assertThat(user.getBuilding()).isEqualTo("3号楼");
        assertThat(user.getRoomNo()).isEqualTo("303");
        // 导航栏显示新名字
        mvc.perform(get("/student/home").session(stu)).andExpect(content().string(containsString("新名字")));
        // 报修表单默认带出新的宿舍
        mvc.perform(get("/student/orders/new").session(stu)).andExpect(content().string(containsString("303")));
    }

    @Test
    void logout_invalidatesSession() throws Exception {
        MockHttpSession w1 = login("W001", REPAIRER_PWD);
        mvc.perform(post("/logout").session(w1)).andExpect(redirectedUrl("/login"));
        assertThat(w1.isInvalid()).isTrue();
    }

    /**
     * 并发接单：多个维修人员同时接同一单，只有一个成功。
     * 验证 RepairOrderMapper.accept 中 "WHERE status = 'PENDING'" 条件更新的效果。
     */
    @Test
    void concurrentAccept_onlyOneSucceeds() throws Exception {
        registerStudent("S8001", "abc123");
        MockHttpSession stu = login("S8001", "abc123");
        Long id = submitOrder(stu, null, "并发测试");
        List<Long> repairerIds = List.of(
                userMapper.findByUsername("W001").getId(),
                userMapper.findByUsername("W002").getId(),
                userMapper.findByUsername("W003").getId());

        int threads = 12;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Long rid = repairerIds.get(i % repairerIds.size());
            futures.add(pool.submit(() -> {
                start.await();
                try {
                    orderService.accept(id, rid);
                    success.incrementAndGet();
                } catch (com.dorm.repair.common.BusinessException ignored) {
                    // 预期：其余线程全部失败
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get(10, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(success.get()).isEqualTo(1);
        assertThat(orderMapper.findById(id).getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);
    }

    // ============================ 辅助方法 ============================

    private void registerStudent(String studentNo, String password) throws Exception {
        mvc.perform(post("/register")
                        .param("username", studentNo).param("realName", "学生" + studentNo)
                        .param("password", password).param("confirmPassword", password)
                        .param("building", "1号楼").param("roomNo", "101").param("phone", "13900001111"))
                .andExpect(redirectedUrl("/login"));
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/login").param("username", username).param("password", password))
                .andExpect(redirectedUrl("/"))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    /** 提交报修并返回新工单 id（从重定向地址 /student/orders/{id} 中解析） */
    private Long submitOrder(MockHttpSession session, MockMultipartFile image, String description) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/student/orders");
        if (image != null) {
            request.file(image);
        }
        request.session(session)
                .param("building", "1号楼").param("roomNo", "101")
                .param("category", "WATER_ELECTRIC").param("description", description);
        MvcResult result = mvc.perform(request).andExpect(status().is3xxRedirection()).andReturn();
        String url = result.getResponse().getRedirectedUrl();
        assertThat(url).startsWith("/student/orders/");
        return Long.parseLong(url.substring("/student/orders/".length()));
    }
}

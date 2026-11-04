package com.dorm.repair.service;

import com.dorm.repair.common.BusinessException;
import com.dorm.repair.common.Constants;
import com.dorm.repair.dto.CompleteForm;
import com.dorm.repair.dto.OrderForm;
import com.dorm.repair.dto.RateForm;
import com.dorm.repair.entity.RepairOrder;
import com.dorm.repair.enums.OrderStatus;
import com.dorm.repair.mapper.RepairOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 报修工单业务。整个闭环：
 * <pre>
 * 学生 submit → [待接单] → 维修人员 accept → [维修中] → 维修人员 complete → [已完成] → 学生 rate
 * </pre>
 * 数据归属校验（学生只能看自己的单、维修人员只能完成自己接的单）都在这一层完成，
 * 不依赖页面是否显示按钮——即使用户手动改 URL 中的工单 id 也无法越权。
 */
@Service
public class RepairOrderService {

    private final RepairOrderMapper orderMapper;
    private final FileStorageService fileStorageService;

    public RepairOrderService(RepairOrderMapper orderMapper, FileStorageService fileStorageService) {
        this.orderMapper = orderMapper;
        this.fileStorageService = fileStorageService;
    }

    // ======================== 学生端 ========================

    /**
     * 学生提交报修工单，初始状态为"待接单"。
     *
     * @param image 可选的故障图片
     * @return 新工单 id
     */
    public Long submit(Long studentId, OrderForm form, MultipartFile image) {
        if (!Constants.BUILDINGS.contains(form.getBuilding())) {
            throw new BusinessException("请选择正确的楼栋");
        }
        // 先保存图片（内部会校验格式），拿到相对路径再写数据库
        String imagePath = fileStorageService.store(image);

        RepairOrder order = new RepairOrder();
        order.setStudentId(studentId);
        order.setBuilding(form.getBuilding());
        order.setRoomNo(form.getRoomNo().trim());
        order.setCategory(form.getCategory());
        order.setDescription(form.getDescription().trim());
        order.setImagePath(imagePath);
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(LocalDateTime.now());
        try {
            orderMapper.insert(order);
        } catch (RuntimeException e) {
            // 数据库写入失败时删除刚保存的图片，避免产生无主文件
            fileStorageService.delete(imagePath);
            throw e;
        }
        return order.getId();
    }

    /** 学生查看自己的工单列表；status 为 null 表示全部 */
    public List<RepairOrder> listForStudent(Long studentId, OrderStatus status) {
        return orderMapper.listByStudent(studentId, status);
    }

    /** 统计学生各状态工单数量（学生首页展示用） */
    public Map<OrderStatus, Long> countForStudent(Long studentId) {
        Map<OrderStatus, Long> counts = new EnumMap<>(OrderStatus.class);
        for (OrderStatus s : OrderStatus.values()) {
            counts.put(s, 0L);
        }
        for (RepairOrder o : orderMapper.listByStudent(studentId, null)) {
            counts.merge(o.getStatus(), 1L, Long::sum);
        }
        return counts;
    }

    /**
     * 学生查看工单详情：只能看自己的。
     * 工单不存在和不属于自己返回同一提示，不暴露别人的工单是否存在。
     */
    public RepairOrder getForStudent(Long orderId, Long studentId) {
        RepairOrder order = orderMapper.findById(orderId);
        if (order == null || !Objects.equals(order.getStudentId(), studentId)) {
            throw new BusinessException("工单不存在或无权查看");
        }
        return order;
    }

    /**
     * 学生评价：工单必须属于自己、状态为已完成、且尚未评价（每单只能评价一次）。
     */
    public void rate(Long orderId, Long studentId, RateForm form) {
        RepairOrder order = getForStudent(orderId, studentId);
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new BusinessException("维修完成后才能评价");
        }
        if (order.getRating() != null) {
            throw new BusinessException("该工单已评价，不能重复评价");
        }
        String evaluation = StringUtils.hasText(form.getEvaluation()) ? form.getEvaluation().trim() : null;
        // 上面的检查给出友好提示；这里的条件 UPDATE 防止并发重复提交（如连点两次按钮）
        int rows = orderMapper.rate(orderId, studentId, form.getRating(), evaluation, LocalDateTime.now());
        if (rows == 0) {
            throw new BusinessException("该工单已评价，不能重复评价");
        }
    }

    // ======================== 维修人员端 ========================

    /** 待接单池：所有待接单的工单 */
    public List<RepairOrder> listPending() {
        return orderMapper.listPending();
    }

    /**
     * 接单：工单状态 待接单 → 维修中，并记录接单人。
     * 依赖 UPDATE ... WHERE status='PENDING' 的原子性，多人同时接同一单时只有一人成功。
     */
    public void accept(Long orderId, Long repairerId) {
        int rows = orderMapper.accept(orderId, repairerId, LocalDateTime.now());
        if (rows == 0) {
            RepairOrder order = orderMapper.findById(orderId);
            if (order == null) {
                throw new BusinessException("工单不存在");
            }
            throw new BusinessException("手慢了，该工单已被其他维修人员接单");
        }
    }

    /**
     * 维修完成：工单状态 维修中 → 已完成，必须填写维修备注。
     * 只能完成自己接的单。
     */
    public void complete(Long orderId, Long repairerId, CompleteForm form) {
        int rows = orderMapper.complete(orderId, repairerId, form.getRepairNote().trim(), LocalDateTime.now());
        if (rows == 0) {
            throw new BusinessException("只能完成自己接单且处于维修中的工单");
        }
    }

    /** 维修人员查看自己接过的工单；status 为 null 表示全部 */
    public List<RepairOrder> listForRepairer(Long repairerId, OrderStatus status) {
        return orderMapper.listByRepairer(repairerId, status);
    }

    /**
     * 维修人员查看工单详情：可以看待接单池中的工单（决定是否接单），或自己接过的工单；
     * 不能查看别的维修人员正在处理或已完成的工单。
     */
    public RepairOrder getForRepairer(Long orderId, Long repairerId) {
        RepairOrder order = orderMapper.findById(orderId);
        boolean visible = order != null
                && (order.getStatus() == OrderStatus.PENDING || Objects.equals(order.getRepairerId(), repairerId));
        if (!visible) {
            throw new BusinessException("工单不存在或无权查看");
        }
        return order;
    }
}

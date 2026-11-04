package com.dorm.repair.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 维修完成表单：维修备注必填。
 */
public class CompleteForm {

    @NotBlank(message = "请填写维修备注")
    @Size(max = 500, message = "维修备注不能超过 500 字")
    private String repairNote;

    public String getRepairNote() { return repairNote; }
    public void setRepairNote(String repairNote) { this.repairNote = repairNote; }
}

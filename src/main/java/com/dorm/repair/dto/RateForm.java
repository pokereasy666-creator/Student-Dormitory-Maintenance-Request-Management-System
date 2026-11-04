package com.dorm.repair.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 工单评价表单：星级 + 简短文字评价。
 */
public class RateForm {

    @NotNull(message = "请选择星级")
    @Min(value = 1, message = "星级为 1~5")
    @Max(value = 5, message = "星级为 1~5")
    private Integer rating;

    @Size(max = 200, message = "评价内容不能超过 200 字")
    private String evaluation;

    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }

    public String getEvaluation() { return evaluation; }
    public void setEvaluation(String evaluation) { this.evaluation = evaluation; }
}

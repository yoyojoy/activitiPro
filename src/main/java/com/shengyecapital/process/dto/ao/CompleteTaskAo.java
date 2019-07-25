package com.shengyecapital.process.dto.ao;

import lombok.Data;

@Data
public class CompleteTaskAo {

    /**
     * 流程实例ID
     */
    private String processInstanceId;

    /**
     * 业务ID
     */
    private String businessId;

    /**
     * 处理人ID
     */
    private String dealUserId;

    /**
     * 批注信息
     */
    private String commentDescription;

    /**
     * 类型
     */
    private String commentType;

}

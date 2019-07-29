package com.shengyecapital.process.dto.ao;

import com.shengyecapital.process.dto.BaseEnvVo;
import lombok.Data;

import java.util.Map;

@Data
public class CompleteTaskAo extends BaseEnvVo {

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
     * 处理人ID
     */
    private String dealUserName;

    /**
     * 批注信息
     */
    private String comment;

    /**
     * 处理任务时携带的流程参数集合
     */
    private Map<String, Object> variables;

}

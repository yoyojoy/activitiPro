package com.shengyecapital.process.dto.ao;

import com.shengyecapital.common.dto.common.BasePageDto;
import lombok.Data;

@Data
public class RuntimeInstanceListQueryAo extends BasePageDto {

    /**
     * 流程名称
     */
    private String processDefinitionName;

    /**
     * 业务对象名称
     */
    private String businessName;

    private String startTime;

    private String endTime;

    /**
     * 当前任务名称
     */
    private String currentTaskName;

    /**
     * 调用方项目关键key(例: shengye-pay)
     */
    private String applicationName;

    /**
     * 调用方环境标识key(例: 生产->prod)
     */
    private String env;

}

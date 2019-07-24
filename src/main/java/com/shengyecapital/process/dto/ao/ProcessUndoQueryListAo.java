package com.shengyecapital.process.dto.ao;

import com.shengyecapital.common.dto.common.BasePageDto;
import lombok.Data;

@Data
public class ProcessUndoQueryListAo extends BasePageDto {

    /**
     * 流程名称
     */
    private String processName;

    /**
     * 流程发起者
     */
    private String processStarter;

    /**
     * 客户名称
     */
    private String customerName;

    private String startTime;

    private String endTime;
}

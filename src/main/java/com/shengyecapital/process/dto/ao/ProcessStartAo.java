package com.shengyecapital.process.dto.ao;

import com.shengyecapital.process.dto.BaseEnvVo;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Data
public class ProcessStartAo extends BaseEnvVo {

    /**
     * 流程标识key
     */
    private String processDefinitionKey;

    /**
     * 流程发起者
     */
    private String processStarter;

    /**
     * 是否完成第一个任务
     */
    private Boolean startAndCompleteFirst;

    /**
     * 发起流程设定的的参数集合
     */
    Map<String, Object> variablesMap;

    /**
     * 绑定业务ID
     */
    private String businessId;

    /**
     * 冗余客户名称
     */
    private String customerName;
}

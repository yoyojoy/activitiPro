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
     * 流程发起者ID
     */
    private String processStarterId;

    /**
     * 流程发起者姓名
     */
    private String processStarterName;

    /**
     * 发起流程设定的的参数集合
     */
    Map<String, Object> variables;

    /**
     * 绑定业务ID
     */
    private String businessId;

    /**
     * 冗余客户名称
     */
    private String customerName;

    /**
     * 冗余客户ID
     */
    private String customerId;
}

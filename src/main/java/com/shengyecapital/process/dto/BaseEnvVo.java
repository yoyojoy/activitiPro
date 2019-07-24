package com.shengyecapital.process.dto;

import lombok.Data;

@Data
public class BaseEnvVo {

    /**
     * 调用方项目关键key(例: shengye-pay)
     */
    private String applicationName;

    /**
     * 调用方环境标识key(例: 生产->prod)
     */
    private String env;
}

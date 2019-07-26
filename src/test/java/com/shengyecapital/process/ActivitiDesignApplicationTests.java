package com.shengyecapital.process;

import com.shengyecapital.process.dto.ao.ProcessStartAo;
import com.shengyecapital.process.service.process.SerialCountersignAddcmd;
import lombok.extern.slf4j.Slf4j;
import org.activiti.engine.*;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class ActivitiDesignApplicationTests {

	@Autowired
	private IdentityService identityService;
	@Autowired
	private RuntimeService runtimeService;
	@Autowired
	private TaskService taskService;
	@Autowired
	private RepositoryService repositoryService;
	@Autowired
    private ManagementService managementService;
	@Autowired
	private ProcessEngine _processEngine;

	@Test
	public void contextLoads() {
	}

	@Test
	public void test(){
		String tenantId = "shengye-abs-dev";
	    ProcessInstance instance = runtimeService.createProcessInstanceQuery().includeProcessVariables().processInstanceTenantId(tenantId)
				.processInstanceNameLike("测试").singleResult();
    }

}

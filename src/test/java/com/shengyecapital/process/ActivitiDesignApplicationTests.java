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
	public void start(){
		log.info("\n===========================================");
		List<ProcessDefinition> dfs = repositoryService.createProcessDefinitionQuery().list();
		log.info(dfs.size()+"");
		log.info("\n===========================================");
		ProcessStartAo ao = new ProcessStartAo();
		ao.setBusinessId("1001");
		ao.setProcessDefinitionKey("apply_process");
		ao.setProcessStarter("1");
		ao.setStartAndCompleteFirst(false);
		ao.setVariablesMap(null);
		Map<String, Object> vars = new HashMap<>();
		if(!CollectionUtils.isEmpty(ao.getVariablesMap())) {
			vars.putAll(ao.getVariablesMap());
		}
		vars.put("startUser", ao.getProcessStarter());
		//设置开启人
		identityService.setAuthenticatedUserId(ao.getProcessStarter());
		ProcessInstance pi = runtimeService.startProcessInstanceByKey(ao.getProcessDefinitionKey(), ao.getBusinessId(), vars);
		Task task = taskService.createTaskQuery().processInstanceId(pi.getId()).active().singleResult();
		//是否完成该任务
		if (ao.getStartAndCompleteFirst()) {
			//查看办理人是否为空，将启动人设为办理人
			if (task.getAssignee() == null) {
				taskService.setAssignee(task.getId(), ao.getProcessStarter());
			}
			taskService.complete(task.getId());
		}
		log.info("流程实例Id" + pi.getId());
	}

	@Test
	public void complete(){
	    String taskId = "10008";
        managementService.executeCommand(new SerialCountersignAddcmd(taskId, "加签的审批人", false, runtimeService, taskService));
    }

}

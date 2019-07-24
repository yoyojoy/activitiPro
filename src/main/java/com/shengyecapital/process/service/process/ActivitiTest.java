package com.shengyecapital.process.service.process;


import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.activiti.bpmn.BpmnAutoLayout;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.bpmn.model.EndEvent;
import org.activiti.bpmn.model.ExclusiveGateway;
import org.activiti.bpmn.model.Process;
import org.activiti.bpmn.model.SequenceFlow;
import org.activiti.bpmn.model.StartEvent;
import org.activiti.bpmn.model.UserTask;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngineConfiguration;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * 测试以activiti API方式进行流程创建和部署
 */
public class ActivitiTest {

    public static void main(String[] args) {
        try {
            test01("apply_process", "测试");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void test01(String processDefinitionKey, String processDefinitionName) throws IOException {
        System.out.println(".........start...");
        ProcessEngine processEngine = getProcessEngine();

        // 1. Build up the model from scratch
        BpmnModel model = new BpmnModel();
        Process process = new Process();
        model.addProcess(process);
        final String PROCESSID = processDefinitionKey;
        final String PROCESSNAME = processDefinitionName;
        process.setId(PROCESSID);
        process.setName(PROCESSNAME);

        StartEvent startEvent = new StartEvent();
        startEvent.setId("startEvent");
        StartEvent endEvent = new StartEvent();
        startEvent.setId("endEvent");

        process.addFlowElement(createStartEvent());
        process.addFlowElement(createUserTask("task1", "申请", "candidateGroup1"));
        process.addFlowElement(createEndEvent());

        process.addFlowElement(createSequenceFlow("startEvent", "task1", "", ""));
        process.addFlowElement(createSequenceFlow("task1", "endEvent", "", ""));


        // 2. Generate graphical information
        new BpmnAutoLayout(model).execute();

        // 3. Deploy the process to the engine
        Deployment deployment = processEngine.getRepositoryService().createDeployment().addBpmnModel(PROCESSID + ".bpmn", model).name(PROCESSID + "_deployment").deploy();

        // 4. Start a process instance
        ProcessInstance processInstance = processEngine.getRuntimeService().startProcessInstanceByKey(PROCESSID);

        // 5. Check if task is available
        List<Task> tasks = processEngine.getTaskService().createTaskQuery().processInstanceId(processInstance.getId()).list();

        // 6. Save process diagram to a file
        InputStream processDiagram = processEngine.getRepositoryService().getProcessDiagram(processInstance.getProcessDefinitionId());
        FileUtils.copyInputStreamToFile(processDiagram, new File("/deployments/" + PROCESSID + ".png"));

        // 7. Save resulting BPMN xml to a file
        InputStream processBpmn = processEngine.getRepositoryService().getResourceAsStream(deployment.getId(), PROCESSID + ".bpmn");
        FileUtils.copyInputStreamToFile(processBpmn, new File("/deployments/" + PROCESSID + ".bpmn"));

        System.out.println(".........end...");
    }

    protected static ProcessEngine getProcessEngine() {
        ProcessEngine processEngine = ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration()
                .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_FALSE)
                .setJdbcUrl("jdbc:mysql://mysql.dev.syf.com:3306/shengye_process")
                .setJdbcDriver("com.mysql.jdbc.Driver")
                .setJdbcUsername("sy")
                .setJdbcPassword("rhYrE3r4wmb9")
                .setDatabaseSchemaUpdate("true")
                .setJobExecutorActivate(false)
                .buildProcessEngine();
        return processEngine;

    }

    /*任务节点*/
    protected static UserTask createUserTask(String id, String name, String candidateGroup) {
        List<String> candidateGroups = new ArrayList<String>();
        candidateGroups.add(candidateGroup);
        UserTask userTask = new UserTask();
        userTask.setName(name);
        userTask.setId(id);
        userTask.setCandidateGroups(candidateGroups);
        return userTask;
    }

    /*连线*/
    protected static SequenceFlow createSequenceFlow(String from, String to, String name, String conditionExpression) {
        SequenceFlow flow = new SequenceFlow();
        flow.setSourceRef(from);
        flow.setTargetRef(to);
        flow.setName(name);
        if (StringUtils.isNotEmpty(conditionExpression)) {
            flow.setConditionExpression(conditionExpression);
        }
        return flow;
    }

    /*排他网关*/
    protected static ExclusiveGateway createExclusiveGateway(String id) {
        ExclusiveGateway exclusiveGateway = new ExclusiveGateway();
        exclusiveGateway.setId(id);
        return exclusiveGateway;
    }

    /*开始节点*/
    protected static StartEvent createStartEvent() {
        StartEvent startEvent = new StartEvent();
        startEvent.setId("startEvent");
        return startEvent;
    }

    /*结束节点*/
    protected static EndEvent createEndEvent() {
        EndEvent endEvent = new EndEvent();
        endEvent.setId("endEvent");
        return endEvent;
    }

}

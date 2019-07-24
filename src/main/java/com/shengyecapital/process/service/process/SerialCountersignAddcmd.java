package com.shengyecapital.process.service.process;

import lombok.Data;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.impl.bpmn.behavior.AbstractBpmnActivityBehavior;
import org.activiti.engine.impl.bpmn.behavior.SequentialMultiInstanceBehavior;
import org.activiti.engine.impl.bpmn.behavior.UserTaskActivityBehavior;
import org.activiti.engine.impl.el.JuelExpression;
import org.activiti.engine.impl.interceptor.Command;
import org.activiti.engine.impl.interceptor.CommandContext;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.activiti.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.activiti.engine.impl.persistence.entity.TaskEntity;
import org.activiti.engine.impl.pvm.process.ActivityImpl;
import org.activiti.engine.runtime.Execution;
import org.activiti.engine.task.Task;
import org.springframework.beans.BeanUtils;

import java.util.ArrayList;
import java.util.List;

@Data
public class SerialCountersignAddcmd implements Command<Void> {

    protected String taskId;

    protected String assignee;

    private Boolean isBefore;

    private RuntimeService runtimeService;

    private TaskService taskService;


    public SerialCountersignAddcmd(String taskId, String assignee, Boolean isBefore, RuntimeService runtimeService, TaskService taskService) {
        super();
        this.taskId = taskId;
        this.assignee = assignee;
        this.isBefore = isBefore;
        this.runtimeService = runtimeService;
        this.taskService = taskService;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Void execute(CommandContext commandContext) {
        Task task = taskService.createTaskQuery().taskId(taskId).active().singleResult();
        ProcessDefinitionEntity deployedProcessDefinition = commandContext
                .getProcessEngineConfiguration()
                .getDeploymentManager()
                .findDeployedProcessDefinitionById(task.getProcessDefinitionId());
        ActivityImpl activity = deployedProcessDefinition.findActivity(task.getTaskDefinitionKey());
        Execution execution = runtimeService.createExecutionQuery().executionId(task.getExecutionId()).singleResult();
        ExecutionEntity ee = (ExecutionEntity) execution;
        ExecutionEntity newExecution=ee.createExecution();
        newExecution.setActive(true);//设置为激活
        newExecution.setConcurrent(true);//设置为不可缺少
        newExecution.setScope(false);
        TaskEntity te = (TaskEntity) task;

        //将现有任务删除
//        commandContext.getTaskEntityManager().deleteTask(te, TaskEntity.DELETE_REASON_DELETED, true);
//        ee.removeTask(te);
        //给Activity设置自定义behavior
        UserTaskActivityBehavior userTaskActivityBehavior = (UserTaskActivityBehavior) activity.getActivityBehavior();
//        AbstractBpmnActivityBehavior userTaskActivityBehavior = sequentialMultiInstanceBehavior.getInnerActivityBehavior();
        MySequentialMultiInstanceBehavior mySequentialMultiInstanceBehavior = new MySequentialMultiInstanceBehavior(activity, userTaskActivityBehavior, true);
        BeanUtils.copyProperties(userTaskActivityBehavior, mySequentialMultiInstanceBehavior);
        userTaskActivityBehavior.setMultiInstanceActivityBehavior(mySequentialMultiInstanceBehavior);
        activity.setActivityBehavior(mySequentialMultiInstanceBehavior);
        JuelExpression collectionExpression = (JuelExpression) userTaskActivityBehavior.getMultiInstanceActivityBehavior().getCollectionExpression();
//        JuelExpression collectionExpression = (JuelExpression) userTaskActivityBehavior.getMultiInstanceActivityBehavior().getCompletionConditionExpression();
        if(collectionExpression != null) {
            //找出集合对应名字
            String expressionText = collectionExpression.getExpressionText();
            expressionText = expressionText.replace("$", "");
            expressionText = expressionText.replace("{", "");
            expressionText = expressionText.replace("}", "");
            //修改流程变量
            List<String> assigneeList = new ArrayList<>();
            assigneeList = (List<String>) runtimeService.getVariable(ee.getId(), expressionText);
            if (isBefore) {
                //插入指定位置之前
                assigneeList.add(assigneeList.indexOf(te.getAssignee()), assignee);
            } else {
                //插入指定位置之后
                assigneeList.add(assigneeList.indexOf(te.getAssignee()) + 1, assignee);
            }
            runtimeService.setVariable(ee.getId(), expressionText, assigneeList);
        }
        //执行实例设置Activity，再转信号执行
        ee.setActivity(activity);
        ee.signal(null, null);
        //要将Behavior重写一下，实现串行加签功能

        return null;
    }
}

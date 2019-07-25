package com.shengyecapital.process.service.joy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.shengyecapital.common.dto.common.PageResult;
import com.shengyecapital.common.exception.ServerErrorException;
import com.shengyecapital.common.util.DateTimeUtil;
import com.shengyecapital.process.constant.ProcessConstant;
import com.shengyecapital.process.dto.ao.*;
import com.shengyecapital.process.dto.vo.ProcessDeployedListVo;
import com.shengyecapital.process.dto.vo.ProcessUndoListVo;
import com.shengyecapital.process.dto.vo.RuntimeInstanceListVo;
import com.shengyecapital.process.mapper.ActivitiMapper;
import lombok.extern.slf4j.Slf4j;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.engine.*;
import org.activiti.engine.history.HistoricActivityInstance;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricProcessInstanceQuery;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.activiti.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.activiti.engine.impl.pvm.PvmTransition;
import org.activiti.engine.impl.pvm.process.ActivityImpl;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.Model;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Comment;
import org.activiti.engine.task.Task;
import org.activiti.engine.task.TaskQuery;
import org.activiti.image.ProcessDiagramGenerator;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProcessService {

    @Autowired
    private RepositoryService repositoryService;
    @Autowired
    private RuntimeService runtimeService;
    @Autowired
    private TaskService taskService;
    @Autowired
    private IdentityService identityService;
    @Autowired
    private HistoryService historyService;
    @Autowired
    private ProcessEngine processEngine;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    private ActivitiMapper activitiMapper;

    private String generateTenantId(String applicationName, String env){
       return String.format("%s-%s", applicationName, env);
    }

    /**
     * 热部署流程
     *
     * @param ao
     * @throws Exception
     */
    public void deploy(DeploymentAo ao) throws Exception {
        MultipartFile file = ao.getFile();
        String fileName = file.getOriginalFilename();
        InputStream in = file.getInputStream();
        String xml = IOUtils.toString(in, "UTF-8");
        Document document = DocumentHelper.parseText(xml);
        Element root = document.getRootElement();
        Element process = root.element("process");
        if (process == null) {
            throw new ServerErrorException("流程配置文件有错误, 没有定义流程");
        }
        String processDefinitionKey = process.attribute("id").getValue();
        String processDefinitionName = process.attribute("name").getValue();

        Deployment deployment = repositoryService.createDeployment()
                .name(processDefinitionKey).addString(fileName, xml)
                .tenantId(generateTenantId(ao.getApplicationName(), ao.getEnv()))
                .deploy();
        ///这里的设计原每个流程在同一个商户(tenantId)下只有一个流程定义KEY
        Model model = repositoryService.createModelQuery().modelKey(processDefinitionKey).singleResult();
        if (model != null) {
            //该定义KEY的流程有部署过
            model.setVersion(model.getVersion() + 1);
        } else {
            model = repositoryService.newModel();
            model.setVersion(1);
        }
        model.setName(processDefinitionName);
        model.setKey(processDefinitionKey);
        model.setCategory(ao.getBusinessType());
        model.setDeploymentId(deployment.getId());
        repositoryService.saveModel(model);
        log.info("流程部署成功, ID: {}", deployment.getId());

    }

    /**
     * 发起流程
     *
     * @param ao
     */
    public void startProcess(ProcessStartAo ao) {
        String tenantId = generateTenantId(ao.getApplicationName(), ao.getEnv());
        Map<String, Object> vars = new HashMap<>();
        if (!CollectionUtils.isEmpty(ao.getVariablesMap())) {
            vars.putAll(ao.getVariablesMap());
        }
        //流程的发起人ID
        vars.put(ProcessConstant.PROCESS_STARTER_ID, ao.getProcessStarterId());
        //流程的发起人姓名
        vars.put(ProcessConstant.PROCESS_STARTER_NAME, ao.getProcessStarterName());
        //冗余客户名称到流程实例中
        vars.put(ProcessConstant.CUSTOMER_NAME, ao.getCustomerName());
        //业务对象名称
        vars.put(ProcessConstant.BUSINESS_NAME, ao.getBusinessName());
        ProcessInstance pi = runtimeService.startProcessInstanceByKeyAndTenantId(ao.getProcessDefinitionKey(), tenantId);
        if (pi == null) {
            throw new ServerErrorException("发起流程失败");
        }
        //标识关联到的业务ID
        Task task = taskService.createTaskQuery().processInstanceId(pi.getId()).active().singleResult();
        runtimeService.setVariables(task.getExecutionId(), vars);
        runtimeService.updateBusinessKey(pi.getProcessInstanceId(), ao.getBusinessId());
        //是否完成该任务
        if (ao.getStartAndCompleteFirst()) {
            if (task.getAssignee() == null) {
                taskService.setAssignee(task.getId(), ao.getProcessStarterId());
            }
            taskService.complete(task.getId());
        }
        log.info("流程发起成功, 流程实例ID: {}", pi.getId());
    }

    /**
     * 查询已部署流程列表
     *
     * @param ao
     * @return
     */
    public PageResult<ProcessDeployedListVo> getDeployedProcessList(DeployedProcessListQueryAo ao) {
        Page<ProcessDeployedListVo> page = PageHelper.startPage(ao.getPageNum(), ao.getPageSize());
        StringBuffer sql = new StringBuffer("SELECT m.id_ processDefinitionId, m.version_ version, t.DEPLOY_TIME_ deployTime, m.name_ processDefinitionName, \n" +
                "\t\t\tm.key_ processDefinitionKey, n.category_ businessType  from (select a.* from ACT_RE_PROCDEF a RIGHT JOIN \n" +
                "\t(select MAX(VERSION_) version, KEY_ processDefKey from ACT_RE_PROCDEF GROUP BY KEY_) b\n" +
                "\ton a.VERSION_=b.version and a.KEY_=b.processDefKey) m LEFT JOIN ACT_RE_DEPLOYMENT t on m.DEPLOYMENT_ID_=t.ID_\n" +
                "\tLEFT JOIN ACT_RE_MODEL n on n.DEPLOYMENT_ID_=t.ID_ where 1=1 ");
        if (StringUtils.isNotBlank(ao.getProcessDefinitionKey())) {
            sql.append("and m.KEY_ like concat(%,").append(ao.getProcessDefinitionKey()).append(" %) ");
        }
        if (StringUtils.isNotBlank(ao.getProcessDefinitionName())) {
            sql.append("and m.NAME_ like concat(%,").append(ao.getProcessDefinitionName()).append(" %) ");
        }
        if (StringUtils.isNotBlank(ao.getBusinessType())) {
            sql.append("and n.CATEGORY_ like concat(%,").append(ao.getBusinessType()).append(" %) ");
        }
        if (StringUtils.isNotBlank(ao.getStartTime())) {
            sql.append("and t.DEPLOY_TIME_ >= DATE_FORMT(").append(ao.getStartTime()).append(", '%Y-%m-%d') ");
        }
        if (StringUtils.isNotBlank(ao.getEndTime())) {
            sql.append("and t.DEPLOY_TIME_ <= DATE_FORMT(").append(ao.getEndTime()).append(", '%Y-%m-%d') ");
        }
        List<ProcessDeployedListVo> data = activitiMapper.queryDeployedProcessesList(sql.toString());
        PageResult<ProcessDeployedListVo> pageResult = new PageResult<>();
        pageResult.setRecords(data);
        pageResult.setTotalPages(page.getPages());
        pageResult.setTotalRecords(page.getTotal());
        return pageResult;
    }

    public void removeDeployedProcess(String deployId) {
        //这里是否有需要进行级联的删除,包含流程定义的历史流程信息
        repositoryService.deleteDeployment(deployId);
    }

    /**
     * 待办任务列表查询
     *
     * @param ao
     * @return
     */
    public PageResult<ProcessUndoListVo> getUndoProcessList(ProcessUndoQueryListAo ao) throws Exception {
        if(StringUtils.isBlank(ao.getProcessStarterId())){
            throw new ServerErrorException("查询个人待办缺少必须参数");
        }
        Page<ProcessUndoListVo> page = PageHelper.startPage(ao.getPageNum(), ao.getPageSize());
        List<ProcessUndoListVo> result = new ArrayList<>();
        HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery();
        if (StringUtils.isNotBlank(ao.getStartTime())) {
            query.finishedAfter(DateTimeUtil.parseToDate(ao.getStartTime(), DateTimeUtil.FMT_yyyyMMdd));
        }
        if (StringUtils.isNotBlank(ao.getEndTime())) {
            query.finishedBefore(DateTimeUtil.parseToDate(ao.getEndTime(), DateTimeUtil.FMT_yyyyMMdd));
        }
        if (StringUtils.isNotEmpty(ao.getCustomerName())) {
            query.variableValueEquals(ProcessConstant.CUSTOMER_NAME, ao.getCustomerName());
        }
        if (StringUtils.isNotBlank(ao.getProcessDefinitionName())) {
            query.processDefinitionName(ao.getProcessDefinitionName());
        }
        query.variableValueEquals(ProcessConstant.PROCESS_STARTER_ID, ao.getProcessStarterId());
        List<HistoricProcessInstance> processInstances = query.unfinished().includeProcessVariables().orderByProcessInstanceStartTime().asc().list();
        if (!CollectionUtils.isEmpty(processInstances)) {
            result = processInstances.stream().map(processInstance -> {
                ProcessUndoListVo target = new ProcessUndoListVo();
                Map<String, Object> map = processInstance.getProcessVariables();
                String processStarterName = map.get(ProcessConstant.PROCESS_STARTER_NAME).toString();
                String customerName = map.get(ProcessConstant.CUSTOMER_NAME).toString();
                //流程名称
                target.setProcessName(processInstance.getProcessDefinitionName());
                //业务ID
                target.setBusinessId(processInstance.getBusinessKey());
                //发起人ID
                target.setProcessStarterId(processInstance.getStartUserId());
                //发起人姓名
                target.setProcessStarterName(processStarterName);
                //客户名称
                target.setCustomerName(customerName);
                //流程发起时间
                target.setProcessStartTime(processInstance.getStartTime());
                target.setProcessInstanceId(processInstance.getId());
                return target;
            }).collect(Collectors.toList());
        }
        PageResult<ProcessUndoListVo> pageResult = new PageResult<>();
        pageResult.setRecords(result);
        pageResult.setTotalPages(page.getPages());
        pageResult.setTotalRecords(page.getTotal());
        return pageResult;
    }

    /**
     * 个人任务列表查询
     *
     * @param ao
     * @return
     */
    public PageResult<ProcessUndoListVo> getPersonalUndoTaskList(ProcessUndoQueryListAo ao) throws Exception {
        if(StringUtils.isBlank(ao.getProcessStarterId())){
            throw new ServerErrorException("查询个人待办缺少必须参数");
        }
        Page<ProcessUndoListVo> page = PageHelper.startPage(ao.getPageNum(), ao.getPageSize());
        List<ProcessUndoListVo> result = new ArrayList<>();
        TaskQuery query = taskService.createTaskQuery();
        query.taskAssignee(ao.getProcessStarterId());
        if (StringUtils.isNotBlank(ao.getStartTime())) {
            query.taskCreatedAfter(DateTimeUtil.parseToDate(ao.getStartTime(), DateTimeUtil.FMT_yyyyMMdd));
        }
        if (StringUtils.isNotBlank(ao.getEndTime())) {
            query.taskCreatedBefore(DateTimeUtil.parseToDate(ao.getEndTime(), DateTimeUtil.FMT_yyyyMMdd));
        }
        if (StringUtils.isNotEmpty(ao.getCustomerName())) {
            query.processVariableValueLikeIgnoreCase(ProcessConstant.CUSTOMER_NAME, ao.getCustomerName());
        }
        if (StringUtils.isNotBlank(ao.getProcessDefinitionName())) {
            query.processDefinitionNameLike(ao.getProcessDefinitionName());
        }
        List<Task> tasks = query.active().includeProcessVariables().orderByTaskCreateTime().asc().list();
        if (!CollectionUtils.isEmpty(tasks)) {
            result = tasks.stream().map(task -> {
                ProcessUndoListVo target = new ProcessUndoListVo();
                ProcessDefinition definition = repositoryService.createProcessDefinitionQuery().processDefinitionId(task.getProcessDefinitionId()).singleResult();
                ProcessInstance instance = runtimeService.createProcessInstanceQuery().processInstanceId(task.getProcessInstanceId()).singleResult();
                Map<String, Object> map = task.getProcessVariables();
                String processStarterId = map.get(ProcessConstant.PROCESS_STARTER_ID).toString();
                String processStarterName = map.get(ProcessConstant.PROCESS_STARTER_NAME).toString();
                String customerName = map.get(ProcessConstant.CUSTOMER_NAME).toString();
                //流程名称
                target.setProcessName(definition.getName());
                //业务ID
                target.setBusinessId(instance.getBusinessKey());
                //发起人ID
                target.setProcessStarterId(processStarterId);
                //发起人姓名
                target.setProcessStarterName(processStarterName);
                //客户名称
                target.setCustomerName(customerName);
                //流程发起时间
                target.setProcessStartTime(task.getCreateTime());
                //环节名称
                target.setTaskName(task.getName());
                //实例ID
                target.setProcessInstanceId(task.getProcessInstanceId());
                return target;
            }).collect(Collectors.toList());
        }
        PageResult<ProcessUndoListVo> pageResult = new PageResult<>();
        pageResult.setRecords(result);
        pageResult.setTotalPages(page.getPages());
        pageResult.setTotalRecords(page.getTotal());
        return pageResult;
    }

    /**
     * 查看流程文件
     * @param definitionId
     * @return
     */
    public void viewProcessDeployResource(String definitionId, String resourceType, HttpServletResponse response) {
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(definitionId).singleResult();
        if (processDefinition == null) {
            throw new ServerErrorException("流程定义不存在");
        }
        String resourceName = "";
        if (resourceType.equals("image")) {
            resourceName = processDefinition.getDiagramResourceName();
        } else if (resourceType.equals("xml")) {
            resourceName = processDefinition.getResourceName();
        }
        InputStream resourceAsStream = repositoryService.getResourceAsStream(processDefinition.getDeploymentId(), resourceName);
        try {
            IOUtils.copy(resourceAsStream, response.getOutputStream());
        } catch (IOException e) {
            log.error("查询流程资源失败", e);
        }
    }

    /**
     * 读取带流程运行时的图片
     */
    public void viewProcessRuntimeImage(String processInstanceId, HttpServletResponse response) {
        //获取历史流程实例
        HistoricProcessInstance processInstance =  historyService.createHistoricProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();
        //获取流程图
        BpmnModel bpmnModel = repositoryService.getBpmnModel(processInstance.getProcessDefinitionId());
        ProcessDiagramGenerator diagramGenerator = processEngine.getProcessEngineConfiguration().getProcessDiagramGenerator();
        ProcessDefinitionEntity definitionEntity = (ProcessDefinitionEntity)repositoryService.getProcessDefinition(processInstance.getProcessDefinitionId());
        List<HistoricActivityInstance> highLightedActivitList =  historyService.createHistoricActivityInstanceQuery().processInstanceId(processInstanceId).list();
        //高亮环节id集合
        List<String> highLightedActivitis = new ArrayList<>();
        //高亮线路id集合
        List<String> highLightedFlows = getHighLightedFlows(definitionEntity,highLightedActivitList);
        //当前流程实例执行到哪个节点
        ExecutionEntity execution = (ExecutionEntity) runtimeService.createExecutionQuery().executionId(processInstanceId).singleResult();
        highLightedActivitis.add(execution.getActivityId());
        //中文显示的是口口口，设置字体就好了
        InputStream imageStream = diagramGenerator.generateDiagram(bpmnModel, "png", highLightedActivitis, highLightedFlows, "宋体", "宋体", "宋体", null, 1.0);
        try {
            IOUtils.copy(imageStream, response.getOutputStream());
        } catch (IOException e) {
            log.error("查询流程资源失败", e);
        }

    }

    /**
     * 获取需要高亮的线
     * @param processDefinitionEntity
     * @param historicActivityInstances
     * @return
     */
    private List<String> getHighLightedFlows(ProcessDefinitionEntity processDefinitionEntity, List<HistoricActivityInstance> historicActivityInstances) {
        List<String> highFlows = new ArrayList<>();
        for (int i = 0; i < historicActivityInstances.size() - 1; i++) {
            ActivityImpl activityImpl = processDefinitionEntity.findActivity(historicActivityInstances.get(i).getActivityId());
            List<ActivityImpl> sameStartTimeNodes = new ArrayList<>();
            ActivityImpl sameActivityImpl1 = processDefinitionEntity.findActivity(historicActivityInstances.get(i + 1).getActivityId());
            sameStartTimeNodes.add(sameActivityImpl1);
            for (int j = i + 1; j < historicActivityInstances.size() - 1; j++) {
                HistoricActivityInstance activityImpl1 = historicActivityInstances.get(j);
                HistoricActivityInstance activityImpl2 = historicActivityInstances.get(j + 1);
                if (activityImpl1.getStartTime().equals(activityImpl2.getStartTime())) {
                    ActivityImpl sameActivityImpl2 = processDefinitionEntity.findActivity(activityImpl2.getActivityId());
                    sameStartTimeNodes.add(sameActivityImpl2);
                } else {
                    break;
                }
            }
            List<PvmTransition> pvmTransitions = activityImpl.getOutgoingTransitions();
            for (PvmTransition pvmTransition : pvmTransitions) {
                ActivityImpl pvmActivityImpl = (ActivityImpl) pvmTransition.getDestination();
                if (sameStartTimeNodes.contains(pvmActivityImpl)) {
                    highFlows.add(pvmTransition.getId());
                }
            }
        }
        return highFlows;
    }

    public PageResult<RuntimeInstanceListVo> getProcessRuntimeInstanceList(RuntimeInstanceListQueryAo ao) {
        Page<RuntimeInstanceListVo> page = PageHelper.startPage(ao.getPageNum(), ao.getPageSize());
        List<RuntimeInstanceListVo> result = new ArrayList<>();
        StringBuffer sql = new StringBuffer("select c.PROC_INST_ID_ processInstanceId, c.PROC_DEF_ID_ processDefinitionId, a.NAME_ processDefinitionName,b.NAME_ currentTaskName, c.START_TIME_ createTime,\n" +
                "c.BUSINESS_KEY_ businessId from ACT_HI_PROCINST c LEFT JOIN ACT_HI_ACTINST t on c.PROC_INST_ID_=t.ID_\n" +
                "LEFT JOIN ACT_RE_PROCDEF a on a.ID_=c.PROC_DEF_ID_\n" +
                "LEFT JOIN ACT_RU_TASK b on c.PROC_INST_ID_=b.PROC_INST_ID_ and b.PROC_DEF_ID_=c.PROC_DEF_ID_ where 1=1 ");
        if(StringUtils.isNotBlank(ao.getProcessDefinitionName())){
            sql.append("and a.NAME_ like concat(%,").append(ao.getProcessDefinitionName()).append(" %) ");
        }
        if(StringUtils.isNotBlank(ao.getBusinessName())){
            sql.append("EXISTS (select 1 from ACT_HI_VARINST m where m.PROC_INST_ID_=c.PROC_INST_ID_ and m.NAME_='businessName' and m.TEXT_ like CONCAT('%',").append(ao.getBusinessName()).append(",'%')) ");
        }
        if(StringUtils.isNotBlank(ao.getStartTime())){
            sql.append("and c.START_TIME_ >= DATE_FORMAT(").append(ao.getStartTime()).append(",'%Y-%m-%d') ");
        }
        if(StringUtils.isNotBlank(ao.getEndTime())){
            sql.append("and c.START_TIME_ <= DATE_FORMAT(").append(ao.getEndTime()).append(",'%Y-%m-%d') ");
        }
        if(StringUtils.isNotBlank(ao.getCurrentTaskName())){
            sql.append("and b.NAME_ like concat(%,").append(ao.getCurrentTaskName()).append(" %) ");
        }
        List<RuntimeInstanceListVo> list = activitiMapper.queryRuntimeInstanceInfoList(sql.toString());
        PageResult<RuntimeInstanceListVo> pageResult = new PageResult<>();
        pageResult.setRecords(list);
        pageResult.setTotalPages(page.getPages());
        pageResult.setTotalRecords(page.getTotal());
        return pageResult;
    }

    /**
     * 查询某流程实例的批注列表
     * @param processInstanceId
     * @return
     */
    public List<Comment> getProcessComments(String processInstanceId) {
        List<Comment> historyCommnets = new ArrayList<>();
        List<HistoricActivityInstance> hais = historyService.createHistoricActivityInstanceQuery().processInstanceId(processInstanceId)
                .activityType("userTask").orderByHistoricActivityInstanceEndTime().desc().list();
        for (HistoricActivityInstance hai : hais) {
            String historytaskId = hai.getTaskId();
            List<Comment> comments = taskService.getTaskComments(historytaskId);
            if(comments!=null && comments.size()>0){
                historyCommnets.addAll(comments);
            }
        }
        return historyCommnets;
    }

    /**
     * 任务处理,并添加批注
     * @param ao
     */
    public void taskProcess(CompleteTaskAo ao){
        taskService.addComment(ao.getProcessInstanceId(), ao.getCommentType(), ao.getCommentDescription());
        Task task = taskService.createTaskQuery().processInstanceId(ao.getProcessInstanceId())
                .processInstanceBusinessKey(ao.getBusinessId()).active().singleResult();
        taskService.setAssignee(task.getId(), ao.getDealUserId());
        taskService.complete(task.getId());
    }

}

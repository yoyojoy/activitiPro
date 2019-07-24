package com.shengyecapital.process.service.joy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.shengyecapital.common.dto.common.PageResult;
import com.shengyecapital.common.exception.ServerErrorException;
import com.shengyecapital.common.util.DateTimeUtil;
import com.shengyecapital.process.constant.ProcessConstant;
import com.shengyecapital.process.dto.ao.DeployedProcessListQueryAo;
import com.shengyecapital.process.dto.ao.DeploymentAo;
import com.shengyecapital.process.dto.ao.ProcessStartAo;
import com.shengyecapital.process.dto.ao.ProcessUndoQueryListAo;
import com.shengyecapital.process.dto.vo.ProcessDeployedListVo;
import com.shengyecapital.process.dto.vo.ProcessUndoListVo;
import com.shengyecapital.process.mapper.ActivitiMapper;
import lombok.extern.slf4j.Slf4j;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.engine.*;
import org.activiti.engine.history.HistoricActivityInstance;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricProcessInstanceQuery;
import org.activiti.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.activiti.engine.impl.context.Context;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.activiti.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.activiti.engine.impl.pvm.PvmTransition;
import org.activiti.engine.impl.pvm.process.ActivityImpl;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.Model;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
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
import java.io.OutputStream;
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


    /**
     * 热部署流程
     *
     * @param ao
     * @throws Exception
     */
    public void deploy(DeploymentAo ao) throws Exception {
        String tenantId = ao.getApplicationName() + "-" + ao.getEnv();
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

        String name = fileName.substring(0, fileName.lastIndexOf("."));
        Deployment deployment = repositoryService.createDeployment()
                .name(name).addString(fileName, xml)
                .tenantId(tenantId).deploy();
        ///这里的设计原每个流程在同一个商户(tenantId)下只有一个流程定义KEY
        Model model = repositoryService.createModelQuery().modelKey(processDefinitionKey).modelTenantId(tenantId).singleResult();
        if (model != null) {
            //该定义KEY的流程有部署过
            model.setVersion(model.getVersion() + 1);
        } else {
            model = repositoryService.newModel();
            model.setVersion(1);
        }
        model.setTenantId(tenantId);
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
        String tenantId = ao.getApplicationName() + "-" + ao.getEnv();
        Map<String, Object> vars = new HashMap<>();
        if (!CollectionUtils.isEmpty(ao.getVariablesMap())) {
            vars.putAll(ao.getVariablesMap());
        }
        vars.put("startUser", ao.getProcessStarter());
        vars.put("customerName", ao.getCustomerName());
        ProcessInstance pi = runtimeService.startProcessInstanceByKeyAndTenantId(ao.getProcessDefinitionKey(), tenantId);
        if (pi == null) {
            throw new ServerErrorException("发起流程失败");
        }
        runtimeService.updateBusinessKey(pi.getProcessInstanceId(), ao.getBusinessId());
        Task task = taskService.createTaskQuery().processInstanceId(pi.getId()).active().singleResult();
        //是否完成该任务
        if (ao.getStartAndCompleteFirst()) {
            //查看办理人是否为空，将启动人设为办理人
            if (task.getAssignee() == null) {
                taskService.setAssignee(task.getId(), ao.getProcessStarter());
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
        Page<ProcessUndoListVo> page = PageHelper.startPage(ao.getPageNum(), ao.getPageSize());
        List<ProcessUndoListVo> result = new ArrayList<>();
        HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery();
        if (StringUtils.isNotBlank(ao.getStartTime())) {
            query.finishedAfter(DateTimeUtil.parseToDate(ao.getStartTime(), DateTimeUtil.FMT_yyyyMMdd));
        }
        if (StringUtils.isNotBlank(ao.getEndTime())) {
            query.finishedBefore(DateTimeUtil.parseToDate(ao.getEndTime(), DateTimeUtil.FMT_yyyyMMdd));
        }
        if (StringUtils.isEmpty(ao.getCustomerName())) {
            query.variableValueEquals(ProcessConstant.CUSTOMER_NAME, ao.getCustomerName());
        }
        if (StringUtils.isNotBlank(ao.getProcessName())) {
            query.processDefinitionName(ao.getProcessName());
        }
//        query.variableValueEquals(ProcessConstant.PROCESS_STARTER_ID, ao.getProcessStarter());
        List<HistoricProcessInstance> processInstances = query.unfinished().orderByProcessInstanceStartTime().asc().list();
        if (!CollectionUtils.isEmpty(processInstances)) {
            result = processInstances.stream().map(processInstance -> {
                Map<String, Object> map = processInstance.getProcessVariables();
                String processStarterName = map.get(ProcessConstant.PROCESS_STARTER_NAME).toString();
                ProcessUndoListVo target = new ProcessUndoListVo();
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

}

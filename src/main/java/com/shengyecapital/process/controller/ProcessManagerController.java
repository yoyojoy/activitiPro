package com.shengyecapital.process.controller;

import com.shengyecapital.common.dto.common.PageResult;
import com.shengyecapital.process.dto.ao.*;
import com.shengyecapital.process.dto.vo.ProcessDeployedListVo;
import com.shengyecapital.process.dto.vo.ProcessUndoListVo;
import com.shengyecapital.process.dto.vo.RuntimeInstanceListVo;
import com.shengyecapital.process.service.joy.ProcessService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;

@Slf4j
@RestController
public class ProcessManagerController {

    @Autowired
    private ProcessService processService;

    /**
     * 部署流程
     * @param ao
     * @return
     * @throws Exception
     */
    @PostMapping("/process/deploy")
    public void deployProcess(DeploymentAo ao) {
        try{
            processService.deploy(ao);
        }catch (Exception e){
            log.error("流程部署失败, \n{}", e);
        }
    }

    /**
     * 查看已部署的流程列表
     * @param ao
     */
    @PostMapping("/process/definition/list")
    public PageResult<ProcessDeployedListVo> definitionList(DeployedProcessListQueryAo ao) {
        try{
            return  processService.getDeployedProcessList(ao);
        }catch (Exception e){
            log.error("查询流程部署列表失败, \n{}", e);
        }
        return null;
    }

    /**
     * 删除部署的流程
     */
    @PostMapping("/process/deploy/remove/{deployId}")
    public void deploymentList(@PathVariable("deployId") String deployId) {
        try{
            processService.removeDeployedProcess(deployId);
        }catch (Exception e){
            log.error("查询流程部署列表失败, \n{}", e);
        }
    }

    /**
     * 启动流程
     * @return
     */
    @PostMapping("/process/start")
    public void startProcess(ProcessStartAo ao) {
        try{
            processService.startProcess(ao);
        }catch (Exception e){
            log.error("发起流程失败, \n{}", e);
        }
    }

    /**
     * 流程文件
     * @param processDefinitionId
     * @param resourceType {xml | image}
     * @param response
     */
    @GetMapping(value = "/process/file/view")
    public void getProcessXml(@RequestParam("processDefinitionId") String processDefinitionId,
                                                @RequestParam("resourceType") String resourceType,
                                                HttpServletResponse response) {
        processService.viewProcessDeployResource(processDefinitionId, resourceType, response);
    }

    /**
     * 查看某个流程实例运行时高亮图
     * @param processInstanceId
     * @param response
     */
    @GetMapping(value = "/process/runtime/view")
    public void viewProcessRuntimeImage(@RequestParam("processInstanceId") String processInstanceId, HttpServletResponse response) {
        processService.viewProcessRuntimeImage(processInstanceId, response);
    }

    /**
     * 运行时流程实例列表
     * @return
     */
    @PostMapping("/process/instance/runtime/list")
    public PageResult<RuntimeInstanceListVo> runtimeInstancesList(RuntimeInstanceListQueryAo ao) {
        try {
            return processService.getProcessRuntimeInstanceList(ao);
        }catch (Exception e){
            log.error("查询运行时流程实例列表失败, \n{}", e);
        }
        return null;
    }

    /**
     * 流程待办列表
     * @return
     */
    @PostMapping("/process/task/undo/list")
    public PageResult<ProcessUndoListVo> findProcessInstanceUndoList(ProcessUndoQueryListAo ao) {
        try {
            return processService.getUndoProcessList(ao);
        }catch (Exception e){
            log.error("查询流程待办列表失败, \n{}", e);
        }
        return null;
    }

    /**
     * 个人任务待办列表查询
     * @return
     */
    @PostMapping("/process/task/personal/list")
    public PageResult<ProcessUndoListVo> findPersonalTaskList(ProcessUndoQueryListAo ao) {
        try {
            return processService.getPersonalUndoTaskList(ao);
        }catch (Exception e){
            log.error("查询流程待办列表失败, \n{}", e);
        }
        return null;
    }

    /**
     * 任务处理
     */
    @PostMapping("/process/task/complete/{businessId}")
    public void taskProcess(@PathVariable("businessId") String businessId){

    }

}

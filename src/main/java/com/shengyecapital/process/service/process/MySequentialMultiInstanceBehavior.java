package com.shengyecapital.process.service.process;

import org.activiti.engine.ActivitiException;
import org.activiti.engine.delegate.BpmnError;
import org.activiti.engine.impl.bpmn.behavior.AbstractBpmnActivityBehavior;
import org.activiti.engine.impl.bpmn.behavior.SequentialMultiInstanceBehavior;
import org.activiti.engine.impl.pvm.delegate.ActivityExecution;
import org.activiti.engine.impl.pvm.process.ActivityImpl;

import java.util.Collection;


public class MySequentialMultiInstanceBehavior extends SequentialMultiInstanceBehavior {

    private static final long serialVersionUID = 1L;

    Boolean counterSignAdd = false;

    public MySequentialMultiInstanceBehavior(ActivityImpl activity,
                                             AbstractBpmnActivityBehavior innerActivityBehavior, Boolean counterSignAdd) {
        super(activity, innerActivityBehavior);
        this.counterSignAdd = counterSignAdd;
    }

    @Override
    protected void createInstances(ActivityExecution execution) throws Exception {
        super.createInstances(execution);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void leave(ActivityExecution execution) {
        Collection collection = (Collection) collectionExpression.getValue(execution);
        int loopCounter = getLoopVariable(execution, getCollectionElementIndexVariable()) + 1;
        int nrOfInstances = getLoopVariable(execution, NUMBER_OF_INSTANCES);
        int nrOfCompletedInstances = getLoopVariable(execution, NUMBER_OF_COMPLETED_INSTANCES) + 1;
        int nrOfActiveInstances = getLoopVariable(execution, NUMBER_OF_ACTIVE_INSTANCES);
        //需要判断之前完成了几个Task修改流程变量
        if (counterSignAdd) {
            loopCounter = nrOfCompletedInstances - 1;
            nrOfInstances = collection.size();
            nrOfCompletedInstances = nrOfCompletedInstances - 1;
            counterSignAdd = false;
        }
        if (loopCounter != nrOfInstances && !completionConditionSatisfied(execution)) {
            callActivityEndListeners(execution);
        }

        setLoopVariable(execution, NUMBER_OF_INSTANCES, nrOfInstances);
        setLoopVariable(execution, getCollectionElementIndexVariable(), loopCounter);
        setLoopVariable(execution, NUMBER_OF_COMPLETED_INSTANCES, nrOfCompletedInstances);
        logLoopDetails(execution, "instance completed", loopCounter, nrOfCompletedInstances, nrOfActiveInstances, nrOfInstances);

        if (loopCounter >= nrOfInstances || completionConditionSatisfied(execution)) {
            super.leave(execution);
        } else {
            try {
                executeOriginalBehavior(execution, loopCounter);
            } catch (BpmnError error) {
                // re-throw business fault so that it can be caught by an Error Intermediate Event or Error Event Sub-Process in the process
                throw error;
            } catch (Exception e) {
                throw new ActivitiException("Could not execute inner activity behavior of multi instance behavior", e);
            }
        }
    }

    @Override
    public void execute(ActivityExecution execution) throws Exception {
        super.execute(execution);
    }

}

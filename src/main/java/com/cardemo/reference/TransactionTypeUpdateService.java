package com.cardemo.reference;

public interface TransactionTypeUpdateService<WorkflowState, WorkflowMessage>
{
	WorkflowMessage selectMessage(WorkflowState state);

}

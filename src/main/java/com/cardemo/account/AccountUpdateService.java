package com.cardemo.account;

public interface AccountUpdateService<WorkflowState, WorkflowMessage>
{
	WorkflowMessage selectMessage(WorkflowState state);

}

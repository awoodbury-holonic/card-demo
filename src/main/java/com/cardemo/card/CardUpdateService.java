package com.cardemo.card;

public interface CardUpdateService<WorkflowState, WorkflowMessage>
{
	WorkflowMessage selectMessage(WorkflowState state);

}

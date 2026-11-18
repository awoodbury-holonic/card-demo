package com.cardemo.batch;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PendingAuthorizationUnloadTasklet
{
	private static final Logger log = LoggerFactory.getLogger(PendingAuthorizationUnloadTasklet.class);
	@FunctionalInterface
	public interface SemanticWorkflow
	{
		void run();
	}

	private final PendingAuthorizationUnloadResourceLifecycleRepository resourceLifecycle;
	private final SemanticWorkflow semanticWorkflow;

	public PendingAuthorizationUnloadTasklet(
		PendingAuthorizationUnloadResourceLifecycleRepository resourceLifecycle,
		SemanticWorkflow semanticWorkflow)
	{
		this.resourceLifecycle = Objects.requireNonNull(
			resourceLifecycle, "resourceLifecycle");
		this.semanticWorkflow = Objects.requireNonNull(
			semanticWorkflow, "semanticWorkflow");
	}

	public void execute()
	{
		var opened = resourceLifecycle.openAll();
		if (opened.outcome() != PendingAuthorizationUnloadResourceLifecycleRepository.AccessOutcome.COMPLETED)
			throw new IllegalStateException("resource open failed: " + opened.status());
		semanticWorkflow.run();
		var closed = resourceLifecycle.closeAll();
		if (closed.outcome() != PendingAuthorizationUnloadResourceLifecycleRepository.AccessOutcome.COMPLETED)
			throw new IllegalStateException("resource close failed: " + closed.status());
	}
}

package com.cardemo.authorization;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PendingAuthorizationGsamUnloadTasklet
{
	private static final Logger log = LoggerFactory.getLogger(PendingAuthorizationGsamUnloadTasklet.class);
	@FunctionalInterface
	public interface SemanticWorkflow
	{
		void run();
	}

	private final SemanticWorkflow semanticWorkflow;

	public PendingAuthorizationGsamUnloadTasklet(SemanticWorkflow semanticWorkflow)
	{
		this.semanticWorkflow = Objects.requireNonNull(
			semanticWorkflow, "semanticWorkflow");
	}

	public void execute()
	{
		semanticWorkflow.run();
	}
}

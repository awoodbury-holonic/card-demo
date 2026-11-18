package com.cardemo.reference;

import java.util.Objects;

@org.springframework.stereotype.Service
public final class TransactionTypeMutationService
{
	public record UpdateCommand(String key, String value)
	{
		public UpdateCommand
		{
			Objects.requireNonNull(key, "key");
			Objects.requireNonNull(value, "value");
		}
	}

	public enum UpdateOutcome { UPDATED, NOT_FOUND, DEADLOCK, FAILED }
	public enum DeleteOutcome { DELETED, REFERENTIAL_INTEGRITY, NOT_FOUND, FAILED }

	public interface MutationPort
	{
		UpdateOutcome update(String key, String value);
		DeleteOutcome delete(String key);
	}

	private final MutationPort mutations;

	public TransactionTypeMutationService(MutationPort mutations)
	{
		this.mutations = Objects.requireNonNull(mutations, "mutations");
	}

	public UpdateOutcome update(UpdateCommand command)
	{
		Objects.requireNonNull(command, "command");
		return Objects.requireNonNull(
			mutations.update(command.key(), command.value()), "update outcome");
	}

	public DeleteOutcome delete(String key)
	{
		Objects.requireNonNull(key, "key");
		return Objects.requireNonNull(mutations.delete(key),
			"delete outcome");
	}

}

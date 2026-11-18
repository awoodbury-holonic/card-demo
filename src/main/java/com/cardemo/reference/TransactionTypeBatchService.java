package com.cardemo.reference;

import java.util.List;
import java.util.Objects;

public interface TransactionTypeBatchService
{
	enum Action { CREATE, UPDATE, DELETE, IGNORE, REJECT }
	record Value(String key, String value)
	{
		public Value
		{
			Objects.requireNonNull(key, "key");
			Objects.requireNonNull(value, "value");
		}
	}
	record Command(long recordNumber, String sourceOperation, Value value)
	{
		public Command
		{
			Objects.requireNonNull(sourceOperation, "sourceOperation");
			Objects.requireNonNull(value, "value");
		}
	}
	sealed interface Outcome permits Applied, Ignored, Rejected, NotFound, Failed { }
	record Applied(long recordNumber, Action action) implements Outcome { }
	record Ignored(long recordNumber) implements Outcome { }
	record Rejected(long recordNumber, String sourceOperation) implements Outcome { }
	record NotFound(long recordNumber, Action action, String key) implements Outcome { }
	record Failed(long recordNumber, Action action, String diagnostic) implements Outcome { }
	record BatchOutcome(List<Outcome> outcomes, int exitCode)
	{
		public BatchOutcome
		{
			outcomes = List.copyOf(outcomes);
		}
	}

	default Action classify(String sourceOperation)
	{
		return switch (sourceOperation)
		{
			case "A" -> Action.CREATE;
			case "U" -> Action.UPDATE;
			case "D" -> Action.DELETE;
			case "*" -> Action.IGNORE;
			default -> Action.REJECT;
		};
	}

	default BatchOutcome summarize(List<Outcome> outcomes)
	{
		List<Outcome> immutable = List.copyOf(outcomes);
		boolean failed = immutable.stream().anyMatch(outcome ->
			outcome instanceof Rejected || outcome instanceof NotFound ||
			outcome instanceof Failed);
		return new BatchOutcome(immutable, failed ? 4 : 0);
	}

	Outcome apply(Command command);

}

package com.cardemo.batch;

import java.util.Objects;
import java.util.function.Function;

public final class AuthorizationLoadService<R, C, K>
{
	public enum ReadOutcome
	{
		ITEM,
		END,
		FAILED
	}

	public enum InsertOutcome
	{
		INSERTED,
		ALREADY_PRESENT,
		FAILED
	}

	public enum LookupOutcome
	{
		FOUND,
		SKIPPED,
		FAILED
	}

	public record ReadResult<T>(T value, ReadOutcome outcome, String diagnostic)
	{
		public ReadResult
		{
			Objects.requireNonNull(outcome, "outcome");
			diagnostic = diagnostic == null ? "" : diagnostic;
		}
	}

	public record LookupResult<T>(T value, LookupOutcome outcome,
		String diagnostic)
	{
		public LookupResult
		{
			Objects.requireNonNull(outcome, "outcome");
			diagnostic = diagnostic == null ? "" : diagnostic;
		}
	}

	public interface RecordReader<T>
	{
		ReadResult<T> next();
	}

	public interface HierarchicalStore<R, C, K>
	{
		InsertOutcome insertRoot(R root);

		LookupResult<R> findRoot(K key);

		InsertOutcome insertChild(R root, C child);
	}

	public static final class LoadException extends Exception
	{
		private static final long serialVersionUID = 1L;

		public LoadException(String message)
		{
			super(message);
		}
	}

	private final RecordReader<R> roots;
	private final RecordReader<C> children;
	private final Function<C, K> childKey;
	private final HierarchicalStore<R, C, K> store;

	public AuthorizationLoadService(RecordReader<R> roots, RecordReader<C> children,
		Function<C, K> childKey,
		HierarchicalStore<R, C, K> store)
	{
		this.roots = Objects.requireNonNull(roots, "roots");
		this.children = Objects.requireNonNull(children, "children");
		this.childKey = Objects.requireNonNull(childKey, "childKey");
		this.store = Objects.requireNonNull(store, "store");
	}

	public void load() throws LoadException
	{
		loadRoots();
		loadChildren();
	}

	private void loadRoots() throws LoadException
	{
		while (true)
		{
			ReadResult<R> result = checked(roots.next(), "root read result");
			if (result.outcome() == ReadOutcome.END)
			{
				return;
			}
			if (result.outcome() == ReadOutcome.FAILED)
			{
				throw failure("root read", result.diagnostic());
			}
			R root = Objects.requireNonNull(result.value(), "root value");
			accept(store.insertRoot(root), "root insert");
		}
	}

	private void loadChildren() throws LoadException
	{
		while (true)
		{
			ReadResult<C> result = checked(children.next(), "child read result");
			if (result.outcome() == ReadOutcome.END)
			{
				return;
			}
			if (result.outcome() == ReadOutcome.FAILED)
			{
				throw failure("child read", result.diagnostic());
			}
			C child = Objects.requireNonNull(result.value(), "child value");
			K key = Objects.requireNonNull(childKey.apply(child), "child key");
			LookupResult<R> lookup = checked(store.findRoot(key),
				"parent lookup result");
			if (lookup.outcome() == LookupOutcome.SKIPPED)
			{
				continue;
			}
			if (lookup.outcome() == LookupOutcome.FAILED)
			{
				throw failure("parent lookup", lookup.diagnostic());
			}
			R root = Objects.requireNonNull(lookup.value(), "parent value");
			accept(store.insertChild(root, child), "child insert");
		}
	}

	private static <T> T checked(T value, String label)
	{
		return Objects.requireNonNull(value, label);
	}

	private static void accept(InsertOutcome outcome, String operation)
		throws LoadException
	{
		InsertOutcome checked = Objects.requireNonNull(outcome,
			operation + " outcome");
		if (checked == InsertOutcome.FAILED)
		{
			throw failure(operation, "failed");
		}
	}

	private static LoadException failure(String operation, String diagnostic)
	{
		String detail = diagnostic == null || diagnostic.isBlank()
			? "failed" : diagnostic;
		return new LoadException(operation + ": " + detail);
	}

}

package com.cardemo.authorization;

import java.util.Objects;

public final class AuthorizationUnloadService<R, D>
{
	public enum NavigationOutcome
	{
		ITEM,
		END,
		FAILED
	}

	public record NavigationResult<T>(T value, NavigationOutcome outcome,
		String diagnostic)
	{
		public NavigationResult
		{
			Objects.requireNonNull(outcome, "outcome");
			diagnostic = diagnostic == null ? "" : diagnostic;
		}
	}

	public interface RootNavigator<R>
	{
		NavigationResult<R> next();
	}

	public interface ChildNavigator<R, D>
	{
		NavigationResult<D> nextFor(R root);
	}

	public interface RecordSink<T>
	{
		void insert(T value) throws UnloadException;
	}

	public static final class UnloadException extends Exception
	{
		private static final long serialVersionUID = 1L;

		public UnloadException(String message)
		{
			super(message);
		}
	}

	private final RootNavigator<R> roots;
	private final ChildNavigator<R, D> children;
	private final RecordSink<R> rootSink;
	private final RecordSink<D> childSink;

	public AuthorizationUnloadService(RootNavigator<R> roots,
		ChildNavigator<R, D> children, RecordSink<R> rootSink,
		RecordSink<D> childSink)
	{
		this.roots = Objects.requireNonNull(roots, "roots");
		this.children = Objects.requireNonNull(children, "children");
		this.rootSink = Objects.requireNonNull(rootSink, "rootSink");
		this.childSink = Objects.requireNonNull(childSink, "childSink");
	}

	public void unload() throws UnloadException
	{
		while (true)
		{
			NavigationResult<R> result = Objects.requireNonNull(
				roots.next(), "root navigation result");
			if (result.outcome() == NavigationOutcome.END)
			{
				return;
			}
			if (result.outcome() == NavigationOutcome.FAILED)
			{
				throw failure("root navigation", result.diagnostic());
			}
			R root = Objects.requireNonNull(result.value(), "root value");
			rootSink.insert(root);
			drainChildren(root);
		}
	}

	private void drainChildren(R root) throws UnloadException
	{
		while (true)
		{
			NavigationResult<D> result = Objects.requireNonNull(
				children.nextFor(root), "child navigation result");
			if (result.outcome() == NavigationOutcome.END)
			{
				return;
			}
			if (result.outcome() == NavigationOutcome.FAILED)
			{
				throw failure("child navigation", result.diagnostic());
			}
			D child = Objects.requireNonNull(result.value(), "child value");
			childSink.insert(child);
		}
	}

	private static UnloadException failure(String operation, String diagnostic)
	{
		String detail = diagnostic == null || diagnostic.isBlank()
			? "failed" : diagnostic;
		return new UnloadException(operation + ": " + detail);
	}

}

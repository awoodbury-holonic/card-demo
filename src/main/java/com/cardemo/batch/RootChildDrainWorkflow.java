package com.cardemo.batch;

import java.util.Objects;

public final class RootChildDrainWorkflow<R, D>
{
	public interface CheckedSink<T>
	{
		void write(T value) throws ExportException;
	}

	public static final class ExportException extends Exception
	{
		private static final long serialVersionUID = 1L;

		public ExportException(String message)
		{
			super(message);
		}
	}

	private final CheckedSink<R> rootSink;
	private final CheckedSink<D> childSink;

	public RootChildDrainWorkflow(CheckedSink<R> rootSink, CheckedSink<D> childSink)
	{
		this.rootSink = Objects.requireNonNull(rootSink, "rootSink");
		this.childSink = Objects.requireNonNull(childSink, "childSink");
	}

	public void exportEligible(R root, Iterable<D> children)
		throws ExportException
	{
		R checkedRoot = Objects.requireNonNull(root, "root");
		Iterable<D> checkedChildren = Objects.requireNonNull(
			children, "children");
		rootSink.write(checkedRoot);
		for (D child : checkedChildren)
		{
			childSink.write(Objects.requireNonNull(child, "child"));
		}
	}

}

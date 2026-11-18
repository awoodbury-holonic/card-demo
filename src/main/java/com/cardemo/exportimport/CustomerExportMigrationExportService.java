package com.cardemo.exportimport;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public final class CustomerExportMigrationExportService
{
	private CustomerExportMigrationExportService() { }
	public record SourceGroup<S, T>(String kind, Source<S> source,
		Function<S, T> projector)
	{
		public SourceGroup
		{
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(source, "source");
			Objects.requireNonNull(projector, "projector");
		}
	}

	public interface Source<T> extends AutoCloseable
	{
		T readNext() throws Exception;
		@Override void close() throws Exception;
	}

	public interface Sink
	{
		void write(String kind, Object value) throws Exception;
	}

	public record ExportSummary(Map<String, Long> counts)
	{
		public ExportSummary
		{
			counts = Map.copyOf(
				Objects.requireNonNull(counts, "counts"));
		}

		public long total()
		{
			return counts.values().stream().mapToLong(Long::longValue).sum();
		}
	}

	public static ExportSummary export(
		List<SourceGroup<?, ?>> sourceGroups, Sink sink) throws Exception
	{
		var groups = List.copyOf(
			Objects.requireNonNull(sourceGroups, "sourceGroups"));
		Objects.requireNonNull(sink, "sink");
		if (groups.size() != 5)
			throw new IllegalArgumentException("source group count differs from intent");
		var counts = new LinkedHashMap<String, Long>();
		for (SourceGroup<?, ?> group : groups)
			drain(group, sink, counts);
		return new ExportSummary(counts);
	}

	private static <S, T> void drain(SourceGroup<S, T> group, Sink sink,
		Map<String, Long> counts) throws Exception
	{
		try (Source<S> source = group.source())
		{
			for (S value; (value = source.readNext()) != null; )
			{
				sink.write(group.kind(), group.projector().apply(value));
				counts.merge(group.kind(), 1L, Long::sum);
			}
		}
	}
	public record SequencedSourceGroup<K, S, T>(K kind, Source<S> source,
		Function<S, T> projector)
	{
		public SequencedSourceGroup
		{
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(source, "source");
			Objects.requireNonNull(projector, "projector");
		}
	}

	public record ExportEnvelope<K, C, T>(K kind, long sequence, C context,
		T value)
	{
		public ExportEnvelope
		{
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(context, "context");
			Objects.requireNonNull(value, "value");
			if (sequence < 1)
				throw new IllegalArgumentException("sequence must be positive");
		}
	}

	public interface SequencedSink<K, C> extends AutoCloseable
	{
		void write(ExportEnvelope<K, C, ?> value) throws Exception;
		@Override void close() throws Exception;
	}

	public record SequencedExportSummary<K>(long lastSequence,
		Map<K, Long> counts)
	{
		public SequencedExportSummary
		{
			if (lastSequence < 0)
				throw new IllegalArgumentException("sequence must not be negative");
			counts = Map.copyOf(
				Objects.requireNonNull(counts, "counts"));
		}

		public long total()
		{
			return counts.values().stream().mapToLong(Long::longValue).sum();
		}
	}

	public static <K, C> SequencedExportSummary<K> exportSequenced(
		List<? extends SequencedSourceGroup<K, ?, ?>> sourceGroups,
		SequencedSink<K, C> sink, C context) throws Exception
	{
		var groups = List.copyOf(
			Objects.requireNonNull(sourceGroups, "sourceGroups"));
		Objects.requireNonNull(sink, "sink");
		Objects.requireNonNull(context, "context");
		if (groups.size() != 5)
			throw new IllegalArgumentException("source group count differs from intent");
		var counts = new LinkedHashMap<K, Long>();
		long sequence = 0;
		try (sink)
		{
			for (SequencedSourceGroup<K, ?, ?> group : groups)
			{
				counts.putIfAbsent(group.kind(), 0L);
				sequence = drainSequenced(group, sink, context, sequence, counts);
			}
		}
		return new SequencedExportSummary<>(sequence, counts);
	}

	private static <K, C> long drainSequenced(
		SequencedSourceGroup<K, ?, ?> group, SequencedSink<K, C> sink,
		C context, long sequence, Map<K, Long> counts) throws Exception
	{
		return drainSequencedTyped(group, sink, context, sequence, counts);
	}

	private static <K, S, T, C> long drainSequencedTyped(
		SequencedSourceGroup<K, S, T> group, SequencedSink<K, C> sink,
		C context, long sequence, Map<K, Long> counts) throws Exception
	{
		try (Source<S> source = group.source())
		{
			for (S value; (value = source.readNext()) != null; )
			{
				long nextSequence = Math.addExact(sequence, 1);
				sink.write(new ExportEnvelope<>(group.kind(), nextSequence, context,
					group.projector().apply(value)));
				counts.merge(group.kind(), 1L, Math::addExact);
				sequence = nextSequence;
			}
		}
		return sequence;
	}
}

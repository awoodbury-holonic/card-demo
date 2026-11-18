package com.cardemo.exportimport.importing;

import java.util.Objects;

public final class CustomerImportTasklet
{
	private ImportCounts runImport()
	{
		long read = 0;
		long imported = 0;
		long rejected = 0;
		while (true)
		{
			ReadResult next = Objects.requireNonNull(source.next(), "next");
			if (next.outcome() == ReadOutcome.END_OF_INPUT)
				return new ImportCounts(read, imported, rejected);
			read++;
			if (dispatcher.dispatch(next.value()) == DispatchOutcome.IMPORTED)
				imported++;
			else
				rejected++;
		}
	}
	public enum ReadOutcome { RECORD, END_OF_INPUT }
	public enum DispatchOutcome { IMPORTED, REJECTED }

	public record ReadResult(CustomerExportEntry value,
		ReadOutcome outcome, String diagnostic)
	{
		public ReadResult
		{
			Objects.requireNonNull(outcome, "outcome");
			diagnostic = diagnostic == null ? "" : diagnostic;
			if ((outcome == ReadOutcome.RECORD) != (value != null))
				throw new IllegalArgumentException("value must match outcome");
		}
	}

	public record ImportCounts(long read, long imported, long rejected)
	{
		public ImportCounts
		{
			if (read < 0 || imported < 0 || rejected < 0 || imported + rejected != read)
				throw new IllegalArgumentException("invalid import counts");
		}
	}

	public interface ImportSource
	{
		ReadResult next();
	}

	public interface ImportDispatcher
	{
		DispatchOutcome dispatch(CustomerExportEntry value);
	}

	private final CustomerImportResourceLifecycleRepository resourceLifecycle;
	private final ImportSource source;
	private final ImportDispatcher dispatcher;

	public CustomerImportTasklet(
		CustomerImportResourceLifecycleRepository resourceLifecycle,
		ImportSource source, ImportDispatcher dispatcher)
	{
		this.resourceLifecycle = Objects.requireNonNull(
			resourceLifecycle, "resourceLifecycle");
		this.source = Objects.requireNonNull(source, "source");
		this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
	}

	public ImportCounts execute()
	{
		var opened = resourceLifecycle.openAll();
		if (opened.outcome() != CustomerImportResourceLifecycleRepository.AccessOutcome.COMPLETED)
			throw new IllegalStateException("resource open failed: " + opened.status());
		ImportCounts counts = runImport();
		resourceLifecycle.closeAllIgnoringOutcome();
		return counts;
	}
}

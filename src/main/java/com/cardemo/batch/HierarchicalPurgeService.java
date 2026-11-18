package com.cardemo.batch;

import java.util.Objects;

public interface HierarchicalPurgeService<R, D>
{
	enum ReadOutcome
	{
		FOUND,
		EXHAUSTED,
		FAILED
	}

	record ReadResult<T>(ReadOutcome outcome, T value, String diagnostic)
	{
		public ReadResult
		{
			Objects.requireNonNull(outcome, "outcome");
			diagnostic = diagnostic == null ? "" : diagnostic;
			if (outcome == ReadOutcome.FOUND)
				Objects.requireNonNull(value, "value");
		}
	}

	record PurgeReport(long rootsRead, long childrenRead,
		long rootsDeleted, long childrenDeleted, long checkpoints) { }

	interface HierarchyPort<R, D>
	{
		ReadResult<R> nextRoot();

		ReadResult<D> nextChild();

		void deleteCurrentRoot(R root);

		void deleteCurrentChild(D child);
	}

	interface CheckpointPort
	{
		void checkpoint(String checkpointId);
	}

	interface DeletionPolicy<R, D>
	{
		boolean deleteChild(R root, D child);

		boolean deleteRoot(R root);
	}

	PurgeReport purge(HierarchyPort<R, D> hierarchy,
		DeletionPolicy<R, D> policy, CheckpointPort checkpoints,
		String checkpointId);

}

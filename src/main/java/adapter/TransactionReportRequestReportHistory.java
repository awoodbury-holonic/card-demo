package com.cardemo.report;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.LoggerFactory;

// Durable ownership/correlation wraps the existing source queue submission.
// Queue records and their owner commit together; Batch metadata remains the
// authority for an execution's status and completed output path.
@org.springframework.stereotype.Service
public final class TransactionReportRequestReportHistory
{
	public record Tracked(TransactionReportRequestReportSubmissionService.SubmissionResult result, String submissionId) { }
	public record Status(String submissionId, String status, String message, String downloadUrl, String submittedAt) { }
	private final org.springframework.jdbc.core.JdbcTemplate jdbc;
	private final org.springframework.transaction.support.TransactionTemplate transactions;
	private final org.springframework.batch.core.explore.JobExplorer executions;

	public TransactionReportRequestReportHistory(javax.sql.DataSource dataSource,
		org.springframework.transaction.PlatformTransactionManager transactionManager,
		org.springframework.batch.core.explore.JobExplorer executions)
	{
		this.jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
		this.transactions = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
		this.executions = executions;
	}

	public static void requirePrincipal(String principal)
	{
		if (principal == null || principal.isBlank())
			throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED);
	}

	public Tracked submit(String principal,
		Supplier<TransactionReportRequestReportSubmissionService.SubmissionResult> operation)
	{
		requirePrincipal(principal);
		try
		{
			return transactions.execute(transaction ->
			{
				jdbc.queryForObject("SELECT LOCK_ID FROM CARDEMO_REPORT_QUEUE_LOCK WHERE LOCK_ID = 1 FOR UPDATE", Integer.class);
				long previousQueueSequence = jdbc.queryForObject("SELECT COALESCE(MAX(QUEUE_SEQUENCE), 0) FROM CARDEMO_TD_QUEUE WHERE QUEUE_NAME = ?",
					Long.class, "JOBS");
				var result = operation.get();
				if (result.outcome() != TransactionReportRequestReportSubmissionService.Outcome.SUBMITTED)
				{
					transaction.setRollbackOnly();
					// The transaction removes any earlier successful queue writes.
					return new Tracked(new TransactionReportRequestReportSubmissionService.SubmissionResult(result.outcome(), 0), null);
				}
				long last = jdbc.queryForObject("SELECT COALESCE(MAX(QUEUE_SEQUENCE), 0) FROM CARDEMO_TD_QUEUE WHERE QUEUE_NAME = ?",
					Long.class, "JOBS");
				if (last <= previousQueueSequence)
					throw new IllegalStateException("submitted report has no queue records");
				String id = UUID.randomUUID().toString();
				jdbc.update("INSERT INTO CARDEMO_REPORT_SUBMISSION (SUBMISSION_ID, REQUESTED_BY, FIRST_SEQUENCE, LAST_SEQUENCE, SUBMITTED_AT, STATE) "
					+ "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, 'QUEUED')", id, principal, previousQueueSequence + 1, last);
				return new Tracked(result, id);
			});
		}
		catch (org.springframework.dao.DataAccessException failure)
		{
			LoggerFactory.getLogger(getClass()).warn("report submission transaction failed", failure);
			return new Tracked(new TransactionReportRequestReportSubmissionService.SubmissionResult(
				TransactionReportRequestReportSubmissionService.Outcome.FAILED, 0), null);
		}
	}

	public List<Status> recent(String principal)
	{
		requirePrincipal(principal);
		return jdbc.queryForList("SELECT * FROM CARDEMO_REPORT_SUBMISSION WHERE REQUESTED_BY = ? "
			+ "ORDER BY SUBMITTED_AT DESC, SUBMISSION_ID DESC FETCH FIRST 20 ROWS ONLY", principal)
			.stream().map(this::describe).toList();
	}

	public Status status(String principal, String id)
	{
		return describe(owned(principal, id));
	}

	private Map<String, Object> owned(String principal, String id)
	{
		requirePrincipal(principal);
		var rows = jdbc.queryForList("SELECT * FROM CARDEMO_REPORT_SUBMISSION WHERE SUBMISSION_ID = ? AND REQUESTED_BY = ?", id, principal);
		if (rows.isEmpty())
			throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND);
		return rows.get(0);
	}

	private org.springframework.batch.core.JobExecution execution(Map<String, Object> row)
	{
		Number executionId = (Number)row.get("EXECUTION_ID");
		return executionId == null ? null : executions.getJobExecution(executionId.longValue());
	}

	private Status describe(Map<String, Object> row)
	{
		String id = (String)row.get("SUBMISSION_ID");
		String state = (String)row.get("STATE");
		String message = state.equals("QUEUED") ? "Report queued." : "Execution status is not yet available.";
		String download = null;
		var execution = execution(row);
		if (execution != null)
		{
			state = execution.getStatus().name();
			message = switch (execution.getStatus())
			{
				case COMPLETED -> "Report completed.";
				case FAILED, ABANDONED -> "Report generation failed.";
				case STOPPED -> "Report generation stopped.";
				case UNKNOWN -> "Execution status is unavailable.";
				default -> "Report generation is running.";
			};
			if (execution.getStatus() == org.springframework.batch.core.BatchStatus.COMPLETED
				&& execution.getExecutionContext().containsKey("reportOutputPath")
				&& Files.isRegularFile(Path.of(execution.getExecutionContext().getString("reportOutputPath"))))
				download = "/api/reports/transactions/" + id + "/report";
		}
		else if (state.equals("FAILED"))
			message = "Report could not be started.";
		else if (!state.equals("QUEUED"))
			state = "UNKNOWN";
		return new Status(id, state, message, download, row.get("SUBMITTED_AT").toString());
	}

	public org.springframework.http.ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> report(
		String principal, String id, boolean download)
	{
		var row = owned(principal, id);
		var execution = execution(row);
		if (execution == null || execution.getStatus() != org.springframework.batch.core.BatchStatus.COMPLETED
			|| !execution.getExecutionContext().containsKey("reportOutputPath"))
			throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Report is not completed.");
		Path file = Path.of(execution.getExecutionContext().getString("reportOutputPath"));
		if (!Files.isRegularFile(file))
			throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Report output is unavailable.");
		org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody body = output ->
		{
			try (var input = Files.newInputStream(file))
			{
				while (true)
				{
					byte[] record = input.readNBytes(133);
					if (record.length == 0) break;
					if (record.length != 133) throw new IOException("Incomplete report record");
					output.write(new String(record, Charset.forName("IBM037"))
						.stripTrailing().getBytes(StandardCharsets.UTF_8));
					output.write('\n');
				}
			}
		};
		String disposition = (download ? "attachment" : "inline") + "; filename=report-" + id + ".txt";
		return org.springframework.http.ResponseEntity.ok()
			.contentType(new org.springframework.http.MediaType("text", "plain", StandardCharsets.UTF_8))
			.header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, disposition)
			.header(org.springframework.http.HttpHeaders.CACHE_CONTROL, "no-store")
			.body(body);
	}
}

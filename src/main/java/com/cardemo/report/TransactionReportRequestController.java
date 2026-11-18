package com.cardemo.report;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports/transactions")
public final class TransactionReportRequestController
{
	public enum ReportType { MONTHLY, YEARLY, CUSTOM }

	public record ReportRequest(String reportType, String startDate,
		String endDate, String confirmation)
	{
		public ReportRequest
		{
			Objects.requireNonNull(reportType, "reportType");
			Objects.requireNonNull(startDate, "startDate");
			Objects.requireNonNull(endDate, "endDate");
			Objects.requireNonNull(confirmation, "confirmation");
		}
	}

	public record ReportResponse(String outcome, int submittedRecords,
		String message, String submissionId)
	{
		public ReportResponse
		{
			Objects.requireNonNull(outcome, "outcome");
			Objects.requireNonNull(message, "message");
			if (submittedRecords < 0 || submittedRecords > 1000)
				throw new IllegalArgumentException("submitted record count");
		}
	}

	public interface DateValidationPort
	{
		Optional<String> rejection(String date);
	}

	public interface JobRecordPort
	{
		List<String> records(ReportType reportType,
			Optional<LocalDate> startDate,
			Optional<LocalDate> endDate);
	}

	private final TransactionReportRequestReportSubmissionService<String> submissions;
	private final DateValidationPort dates;
	private final JobRecordPort jobs;
	private final TransactionReportRequestReportHistory history;

	public TransactionReportRequestController(
		TransactionReportRequestReportSubmissionService<String> submissions,
		DateValidationPort dates, JobRecordPort jobs, TransactionReportRequestReportHistory history)
	{
		this.submissions = Objects.requireNonNull(submissions,
			"submissions");
		this.dates = Objects.requireNonNull(dates, "dates");
		this.jobs = Objects.requireNonNull(jobs, "jobs");
		this.history = Objects.requireNonNull(history, "history");
	}

	@PostMapping
	public ReportResponse submit(@RequestBody ReportRequest request,
		@org.springframework.web.bind.annotation.SessionAttribute(name = "authenticatedPrincipal", required = false) String principal)
	{
		TransactionReportRequestReportHistory.requirePrincipal(principal);
		ReportRequest value = Objects.requireNonNull(request, "request");
		ReportType reportType;
		try
		{
			reportType = ReportType.valueOf(value.reportType().trim()
				.toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException failure)
		{
			return invalid("Select a report type.");
		}

		Optional<LocalDate> startDate = Optional.empty();
		Optional<LocalDate> endDate = Optional.empty();
		if (reportType == ReportType.CUSTOM)
		{
			try
			{
				startDate = Optional.of(LocalDate.parse(
					value.startDate()));
				endDate = Optional.of(LocalDate.parse(
					value.endDate()));
			}
			catch (DateTimeParseException failure)
			{
				return invalid("Enter a valid custom date range.");
			}
			Optional<String> startFailure =
				dates.rejection(value.startDate());
			if (startFailure.isPresent()) return invalid(startFailure.orElseThrow());
			Optional<String> endFailure = dates.rejection(value.endDate());
			if (endFailure.isPresent()) return invalid(endFailure.orElseThrow());
		}

		List<String> records = List.copyOf(
			jobs.records(reportType, startDate, endDate));
		var tracked = history.submit(principal, () -> submissions.submit(
			new TransactionReportRequestReportSubmissionService.SubmissionRequest<>(
				value.confirmation(), records)));
		var result = tracked.result();
		return new ReportResponse(result.outcome().name(),
			result.submittedRecords(), switch (result.outcome())
			{
				case CONFIRMATION_REQUIRED -> "Confirm report submission.";
				case CANCELLED -> "Report submission cancelled.";
				case SUBMITTED -> "Report submitted for printing.";
				case INVALID_CONFIRMATION -> "Confirmation must be Y or N.";
				case FAILED -> "Unable to submit report.";
			}, tracked.submissionId());
	}

	@org.springframework.web.bind.annotation.GetMapping
	public List<TransactionReportRequestReportHistory.Status> recent(
		@org.springframework.web.bind.annotation.SessionAttribute(name = "authenticatedPrincipal", required = false) String principal)
	{
		return history.recent(principal);
	}

	@org.springframework.web.bind.annotation.GetMapping("/\u007bsubmissionId\u007d")
	public TransactionReportRequestReportHistory.Status status(
		@org.springframework.web.bind.annotation.PathVariable("submissionId") String submissionId,
		@org.springframework.web.bind.annotation.SessionAttribute(name = "authenticatedPrincipal", required = false) String principal)
	{
		return history.status(principal, submissionId);
	}

	@org.springframework.web.bind.annotation.GetMapping("/\u007bsubmissionId\u007d/report")
	public org.springframework.http.ResponseEntity<org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody> report(
		@org.springframework.web.bind.annotation.PathVariable("submissionId") String submissionId,
		@org.springframework.web.bind.annotation.RequestParam(name = "download", defaultValue = "false") boolean download,
		@org.springframework.web.bind.annotation.SessionAttribute(name = "authenticatedPrincipal", required = false) String principal)
	{
		return history.report(principal, submissionId, download);
	}

	private static ReportResponse invalid(String message)
	{
		return new ReportResponse("INVALID", 0,
			Objects.requireNonNull(message, "message"), null);
	}
}

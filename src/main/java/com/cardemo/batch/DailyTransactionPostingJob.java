package com.cardemo.batch;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;

// The source's main line as one chunk-oriented step: before the step, its
// opening display and the opens in source order; each read is one daily
// transaction, which the writer validates and then posts or rejects; end of
// data runs the closes, the counts and the closing display inside the step.
public final class DailyTransactionPostingJob
	implements ItemReader<DailyTransaction>, ItemWriter<DailyTransaction>,
		JobExecutionListener, ExitCodeGenerator
{
	private static final Logger log = LoggerFactory.getLogger(DailyTransactionPostingJob.class);
	public enum PostingDisposition
	{
		POST,
		REJECT
	}

	public record PostingDecision<R>(PostingDisposition disposition,
		R record, String reason)
	{
		public PostingDecision
		{
			Objects.requireNonNull(disposition, "disposition");
			Objects.requireNonNull(record, "record");
			Objects.requireNonNull(reason, "reason");
		}
	}

	// The caller owns the concrete adapter. This port models only the two
	// source-proven continuations and carries one immutable record value.
	public interface PostingPort<R>
	{
		void post(R record);
		void reject(R record, String reason);
	}

	// Per-record preparation and validation belong to the caller because their
	// adapters and policies vary by unit. This loop retains only one value at a
	// time and returns immutable operational evidence.
	public interface PostingValidator<R>
	{
		PostingDecision<R> prepareAndDecide(R record);
	}

	public record PostingCounts(long processed, long rejected)
	{
		public PostingCounts
		{
			if (processed < 0 || rejected < 0 || rejected > processed)
			{
				throw new IllegalArgumentException("invalid posting counts");
			}
		}
	}

	public static PostingDecision<DailyTransaction> decide(
		DailyTransaction record,
		DailyTransactionPostingValidationService.TransactionValidationResult validation)
	{
		Objects.requireNonNull(record, "record");
		Objects.requireNonNull(validation, "validation");
		PostingDisposition disposition = validation.issue()
			== DailyTransactionPostingValidationService.TransactionIssue.NONE
			? PostingDisposition.POST : PostingDisposition.REJECT;
		return new PostingDecision<>(disposition, record, validation.message());
	}

	public static <R> void dispatch(PostingDecision<R> decision,
		PostingPort<? super R> port)
	{
		Objects.requireNonNull(decision, "decision");
		Objects.requireNonNull(port, "port");
		if (decision.disposition() == PostingDisposition.POST)
		{
			port.post(decision.record());
		}
		else
		{
			port.reject(decision.record(), decision.reason());
		}
	}

	public static <R> PostingCounts process(Iterator<R> records,
		PostingValidator<R> validator, PostingPort<? super R> port)
	{
		Objects.requireNonNull(records, "records");
		Objects.requireNonNull(validator, "validator");
		Objects.requireNonNull(port, "port");
		long processed = 0;
		long rejected = 0;
		while (records.hasNext())
		{
			R record = Objects.requireNonNull(records.next(), "record");
			PostingDecision<R> decision = Objects.requireNonNull(
				validator.prepareAndDecide(record), "decision");
			dispatch(decision, port);
			processed++;
			if (decision.disposition() == PostingDisposition.REJECT)
			{
				rejected++;
			}
		}
		return new PostingCounts(processed, rejected);
	}

	// A category balance is read by key before it is written or rewritten:
	// the generated port has only the write half, so its adapter adds this.
	public interface CategoryBalanceLookup
	{
		Optional<DailyTransactionPostingTcatbalFileRepository.TransactionCategoryBalanceSnapshot> findByKey(
			long accountId, String typeCode, long categoryCode);
	}

	// The reject record repeats the input record's image, so the reader
	// keeps the last image it decoded.
	public interface RecordImageSource
	{
		String lastRecordImage();
	}

	private final DailyTransactionPostingDalytranFileRepository input;
	private final DailyTransactionPostingTransactFileRepository posted;
	private final DailyTransactionPostingXrefFileRepository xref;
	private final DailyTransactionPostingDalyrejsFileRepository rejects;
	private final DailyTransactionPostingAccountFileRepository accounts;
	private final DailyTransactionPostingTcatbalFileRepository balances;
	private final CategoryBalanceLookup balanceLookup;
	private final RecordImageSource images;
	private final DailyTransactionPostingStatusFormatter statusFormatter;
	private long transactionCount;
	private long rejectCount;
	private boolean finished;

	public DailyTransactionPostingJob(DailyTransactionPostingDalytranFileRepository input,
		DailyTransactionPostingTransactFileRepository posted,
		DailyTransactionPostingXrefFileRepository xref,
		DailyTransactionPostingDalyrejsFileRepository rejects,
		DailyTransactionPostingAccountFileRepository accounts,
		DailyTransactionPostingTcatbalFileRepository balances,
		CategoryBalanceLookup balanceLookup, RecordImageSource images,
		DailyTransactionPostingStatusFormatter statusFormatter)
	{
		this.input = Objects.requireNonNull(input, "input");
		this.posted = Objects.requireNonNull(posted, "posted");
		this.xref = Objects.requireNonNull(xref, "xref");
		this.rejects = Objects.requireNonNull(rejects, "rejects");
		this.accounts = Objects.requireNonNull(accounts, "accounts");
		this.balances = Objects.requireNonNull(balances, "balances");
		this.balanceLookup = Objects.requireNonNull(balanceLookup, "balanceLookup");
		this.images = Objects.requireNonNull(images, "images");
		this.statusFormatter = Objects.requireNonNull(statusFormatter, "statusFormatter");
	}

	@Override
	public void beforeJob(JobExecution execution)
	{
		transactionCount = 0;
		rejectCount = 0;
		finished = false;
		log.info("START OF EXECUTION OF PROGRAM CBTRN02C");
		var opened = input.openDalytranFile();
		if (opened.outcome() != DailyTransactionPostingDalytranFileRepository.AccessOutcome.COMPLETED)
			throw fail("open", opened.status(), "ERROR OPENING DALYTRAN");
		var openedPosted = posted.openTransactFile();
		if (openedPosted.outcome() != DailyTransactionPostingTransactFileRepository.AccessOutcome.COMPLETED)
			throw fail("open", openedPosted.status(), "ERROR OPENING TRANSACTION FILE");
		var openedXref = xref.openXrefFile();
		if (openedXref.outcome() != DailyTransactionPostingXrefFileRepository.AccessOutcome.COMPLETED)
			throw fail("open", openedXref.status(), "ERROR OPENING CROSS REF FILE");
		var openedRejects = rejects.openDalyrejsFile();
		if (openedRejects.outcome() != DailyTransactionPostingDalyrejsFileRepository.AccessOutcome.COMPLETED)
			throw fail("open", openedRejects.status(), "ERROR OPENING DALY REJECTS FILE");
		var openedAccounts = accounts.openAccountFile();
		if (openedAccounts.outcome() != DailyTransactionPostingAccountFileRepository.AccessOutcome.COMPLETED)
			throw fail("open", openedAccounts.status(), "ERROR OPENING ACCOUNT MASTER FILE");
		var openedBalances = balances.openTcatbalFile();
		if (openedBalances.outcome() != DailyTransactionPostingTcatbalFileRepository.AccessOutcome.COMPLETED)
			throw fail("open", openedBalances.status(), "ERROR OPENING TRANSACTION BALANCE FILE");
	}

	@Override
	public DailyTransaction read()
	{
		if (finished)
			return null;
		var result = input.readNext();
		switch (result.outcome())
		{
		case RECORD:
			return result.value();
		case END_OF_DATA:
			finish();
			return null;
		default:
			throw fail("read", result.status(), "ERROR READING DALYTRAN FILE");
		}
	}

	// End of data: the closes in source order, the counts, the return code
	// and the closing display.
	private void finish()
	{
		var closed = input.closeDalytranFile();
		if (closed.outcome() != DailyTransactionPostingDalytranFileRepository.AccessOutcome.COMPLETED)
			throw fail("close", closed.status(), "ERROR CLOSING DALYTRAN FILE");
		var closedPosted = posted.closeTransactFile();
		if (closedPosted.outcome() != DailyTransactionPostingTransactFileRepository.AccessOutcome.COMPLETED)
			throw fail("close", closedPosted.status(), "ERROR CLOSING TRANSACTION FILE");
		var closedXref = xref.closeXrefFile();
		if (closedXref.outcome() != DailyTransactionPostingXrefFileRepository.AccessOutcome.COMPLETED)
			throw fail("close", closedXref.status(), "ERROR CLOSING CROSS REF FILE");
		var closedRejects = rejects.closeDalyrejsFile();
		// The source displays the cross-reference file's status here, not the
		// rejects file's (its 9300 paragraph); kept as the source behaves.
		if (closedRejects.outcome() != DailyTransactionPostingDalyrejsFileRepository.AccessOutcome.COMPLETED)
			throw fail("close", closedXref.status(), "ERROR CLOSING DAILY REJECTS FILE");
		var closedAccounts = accounts.closeAccountFile();
		if (closedAccounts.outcome() != DailyTransactionPostingAccountFileRepository.AccessOutcome.COMPLETED)
			throw fail("close", closedAccounts.status(), "ERROR CLOSING ACCOUNT FILE");
		var closedBalances = balances.closeTcatbalFile();
		if (closedBalances.outcome() != DailyTransactionPostingTcatbalFileRepository.AccessOutcome.COMPLETED)
			throw fail("close", closedBalances.status(), "ERROR CLOSING TRANSACTION BALANCE FILE");
		log.info("TRANSACTIONS PROCESSED :" + String.format("%09d", transactionCount));
		log.info("TRANSACTIONS REJECTED  :" + String.format("%09d", rejectCount));
		log.info("END OF EXECUTION OF PROGRAM CBTRN02C");
		finished = true;
	}

	@Override
	public void write(Chunk<? extends DailyTransaction> records)
	{
		for (DailyTransaction record : records)
			process(record);
	}

	// One daily transaction: count it, validate it, then post or reject it.
	private void process(DailyTransaction record)
	{
		++transactionCount;
		var validation = DailyTransactionPostingValidationService.validateTransaction(
			xref, accounts, record.cardNumber(), record.amount(),
			record.originTimestamp());
		var decision = decide(record, validation);
		if (decision.disposition() == PostingDisposition.POST)
		{
			post(record, validation.account().orElseThrow());
			return;
		}
		++rejectCount;
		reject(record, rejectCode(validation.issue()), decision.reason());
	}

	// The source's numeric reason for each validation outcome.
	private static int rejectCode(DailyTransactionPostingValidationService.TransactionIssue issue)
	{
		return switch (issue)
		{
		case XREF_FILE_KEY_NOT_FOUND -> 100;
		case ACCOUNT_FILE_KEY_NOT_FOUND -> 101;
		case OVER_LIMIT -> 102;
		case EXPIRED -> 103;
		case NONE -> 0;
		};
	}

	private void post(DailyTransaction record,
		DailyTransactionPostingAccountFileRepository.AccountSnapshot account)
	{
		updateCategoryBalance(record, account.acctId());
		accounts.applySignedBalanceDelta(account, record.amount());
		var written = posted.write(new DailyTransactionPostingTransactFileRepository.TransactionSnapshot(
			record.transactionId(), record.typeCode(), record.categoryCode(),
			record.source(), record.description(), record.amount(),
			record.merchantId(), record.merchantName(), record.merchantCity(),
			record.merchantZip(), record.cardNumber(), record.originTimestamp(),
			DailyTransactionPostingTimestampFormatter.format(LocalDateTime.now())));
		if (written.outcome() != DailyTransactionPostingTransactFileRepository.AccessOutcome.COMPLETED)
			throw fail("write", written.status(), "ERROR WRITING TO TRANSACTION FILE");
	}

	// Read the category balance by key, creating it when absent, then add
	// the amount and write or rewrite it.
	private void updateCategoryBalance(DailyTransaction record, long accountId)
	{
		var existing = balanceLookup.findByKey(accountId, record.typeCode(),
			record.categoryCode());
		if (existing.isEmpty())
			log.info("TCATBAL record not found for key : " + String.format("%011d", accountId)
				+ record.typeCode() + String.format("%04d", record.categoryCode())
				+ ".. Creating.");
		var accumulation = DailyTransactionPostingAccumulatedValueService.apply(
			existing.map(DailyTransactionPostingTcatbalFileRepository.TransactionCategoryBalanceSnapshot::tranCatBal),
			record.amount());
		var balance = new DailyTransactionPostingTcatbalFileRepository.TransactionCategoryBalanceSnapshot(
			accountId, record.typeCode(), record.categoryCode(), accumulation.balance());
		if (accumulation.action() == DailyTransactionPostingAccumulatedValueService.PersistenceAction.CREATE)
		{
			var written = balances.write(balance);
			if (written.outcome() != DailyTransactionPostingTcatbalFileRepository.AccessOutcome.COMPLETED)
				throw fail("write", written.status(), "ERROR WRITING TRANSACTION BALANCE FILE");
		}
		else
		{
			var rewritten = balances.update(balance);
			if (rewritten.outcome() != DailyTransactionPostingTcatbalFileRepository.AccessOutcome.COMPLETED)
				throw fail("rewrite", rewritten.status(), "ERROR REWRITING TRANSACTION BALANCE FILE");
		}
	}

	private void reject(DailyTransaction record, int code, String description)
	{
		String trailer = String.format("%04d", code) + String.format("%-76.76s", description);
		var written = rejects.write(new DailyTransactionPostingDalyrejsFileRepository.RejectedTransactionSnapshot(
			images.lastRecordImage(), trailer));
		if (written.outcome() != DailyTransactionPostingDalyrejsFileRepository.AccessOutcome.COMPLETED)
			throw fail("write", written.status(), "ERROR WRITING TO REJECTS FILE");
	}

	// The source sets RETURN-CODE 4 when any record was rejected.
	@Override
	public int getExitCode()
	{
		return rejectCount > 0 ? 4 : 0;
	}

	public long transactionCount()
	{
		return transactionCount;
	}

	public long rejectCount()
	{
		return rejectCount;
	}

	// The source's abend path: the failing operation's message, the file
	// status, then the abend message; the returned exception ends the job.
	private PostingAccessException fail(String operation, String status, String message)
	{
		log.error(message);
		log.error(statusFormatter.formatStatus(status));
		log.error("ABENDING PROGRAM");
		return new PostingAccessException(operation, status, message);
	}

	public static final class PostingAccessException extends RuntimeException
	{
		private final String operation;
		private final String status;

		private PostingAccessException(String operation, String status, String message)
		{
			super(message);
			this.operation = operation;
			this.status = status;
		}

		public String operation()
		{
			return operation;
		}

		public String status()
		{
			return status;
		}
	}

	// The source ends a failed run with user abend 999; kept as diagnostic data.
	public static final int ABEND_CODE = 999;
}

// Runs the program as a Spring Batch job, the counterpart of its JCL step.
// It is launched by name (--spring.batch.job.enabled=true
// --spring.batch.job.name=dailyTransactionPostingJob), or once at web
// startup with --carddemo.batch.posttran.enabled=true, after the seed imports.
@Configuration
class DailyTransactionPostingJobConfiguration
{
	@Bean
	DailyTransactionPostingJob dailyTransactionPostingProgram(javax.sql.DataSource dataSource,
		Environment environment)
	{
		Charset hostCharset =
			Charset.forName("IBM037");
		var input = new DailyTransactionPostingJobAdapters.SeedInput("seed-data/AWS.M2.CARDDEMO.DALYTRAN.PS", hostCharset);
		var balances = new DailyTransactionPostingJobAdapters.CategoryBalances(dataSource);
		return new DailyTransactionPostingJob(input,
			new DailyTransactionPostingJobAdapters.PostedTransactions(dataSource),
			new DailyTransactionPostingJobAdapters.CrossReferences(dataSource),
			new DailyTransactionPostingJobAdapters.RejectFile(Path.of(environment.getProperty(
				"carddemo.batch.dalyrejs.path", "dalyrejs.dat")), hostCharset),
			new DailyTransactionPostingJobAdapters.Accounts(dataSource),
			balances, balances, input,
			new DailyTransactionPostingStatusFormatter(hostCharset));
	}

	// One record per chunk, so each record is posted before the next read.
	@Bean
	Job dailyTransactionPostingJob(JobRepository jobs,
		PlatformTransactionManager transactions,
		DailyTransactionPostingJob dailyTransactionPostingProgram)
	{
		Step step = new StepBuilder("dailyTransactionPostingStep", jobs)
			.<DailyTransaction, DailyTransaction>chunk(1, transactions)
			.reader(dailyTransactionPostingProgram)
			.writer(dailyTransactionPostingProgram)
			.build();
		return new JobBuilder("dailyTransactionPostingJob", jobs)
			.listener(dailyTransactionPostingProgram)
			.start(step)
			.build();
	}

	// Spring Boot adds its failed-job exit code only when no other exit code
	// generator exists; the program's return code is one, so keep Spring's
	// too, or a failed job of any program would exit 0.
	@Bean
	org.springframework.boot.autoconfigure.batch.JobExecutionExitCodeGenerator
		dailyTransactionPostingFailedJobExitCode()
	{
		return new org.springframework.boot.autoconfigure.batch.JobExecutionExitCodeGenerator();
	}

	// Web startup: run the job once after the seed imports, when enabled.
	@Bean
	@Order(1000)
	ApplicationRunner dailyTransactionPostingStartupRun(Environment environment,
		JobLauncher launcher,
		@Qualifier("dailyTransactionPostingJob") Job dailyTransactionPostingJob)
	{
		return (ApplicationArguments arguments) ->
		{
			if (environment.getProperty("carddemo.batch.posttran.enabled", Boolean.class, false))
				launcher.run(dailyTransactionPostingJob, new JobParametersBuilder()
					.addLong("startedAt", System.currentTimeMillis()).toJobParameters());
		};
	}
}

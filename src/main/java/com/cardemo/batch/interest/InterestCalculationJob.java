package com.cardemo.batch.interest;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;

// The source's main line as one chunk-oriented step over the category balance
// file, in key order. A new account rewrites the previous one (its interest
// added, its cycle totals zeroed) and reads the new account and its card.
// Each balance with a nonzero disclosure rate earns one interest transaction.
public final class InterestCalculationJob
	implements ItemReader<TransactionCategoryBalance>, ItemWriter<TransactionCategoryBalance>,
		JobExecutionListener
{
	private static final Logger log = LoggerFactory.getLogger(InterestCalculationJob.class);
	private static final DateTimeFormatter DB2_TIMESTAMP =
		DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SS'0000'");
	private boolean endOfFile = false;


	// Keyed reads the generated ports leave out: the account by id, its card
	// by the cross-reference's alternate key, and a disclosure group's rate.
	public interface Lookups
	{
		Optional<InterestCalculationAccountFileRepository.AccountSnapshot> account(long accountId);
		Optional<String> cardOfAccount(long accountId);
		Optional<BigDecimal> rate(String groupId, String typeCode, long categoryCode);
	}

	private final InterestCalculationTcatbalFileRepository input;
	private final InterestCalculationXrefFileRepository xref;
	private final InterestCalculationDiscgrpFileRepository groups;
	private final InterestCalculationAccountFileRepository accounts;
	private final InterestCalculationTransactFileRepository output;
	private final Lookups lookups;
	private final InterestCalculationStatusFormatter statusFormatter;
	private final InterestCalculationDerivedValueService interest =
		new InterestCalculationDerivedValueService();
	private final String transactionIdPrefix;
	private Long lastAccountId;
	private boolean firstTime;
	private BigDecimal totalInterest;
	private InterestCalculationAccountFileRepository.AccountSnapshot account;
	private String card;
	private long transactionSuffix;
	private long recordCount;
	private boolean finished;

	public InterestCalculationJob(InterestCalculationTcatbalFileRepository input,
		InterestCalculationXrefFileRepository xref,
		InterestCalculationDiscgrpFileRepository groups,
		InterestCalculationAccountFileRepository accounts,
		InterestCalculationTransactFileRepository output, Lookups lookups,
		InterestCalculationStatusFormatter statusFormatter, String transactionIdPrefix)
	{
		this.input = Objects.requireNonNull(input, "input");
		this.xref = Objects.requireNonNull(xref, "xref");
		this.groups = Objects.requireNonNull(groups, "groups");
		this.accounts = Objects.requireNonNull(accounts, "accounts");
		this.output = Objects.requireNonNull(output, "output");
		this.lookups = Objects.requireNonNull(lookups, "lookups");
		this.statusFormatter = Objects.requireNonNull(statusFormatter, "statusFormatter");
		this.transactionIdPrefix = Objects.requireNonNull(transactionIdPrefix, "transactionIdPrefix");
	}

	@Override
	public void beforeJob(JobExecution execution)
	{
		lastAccountId = null;
		firstTime = true;
		totalInterest = BigDecimal.ZERO;
		transactionSuffix = 0;
		recordCount = 0;
		finished = false;
		log.info("START OF EXECUTION OF PROGRAM CBACT04C");
		var opened = input.openTcatbalFile();
		if (opened.outcome() != InterestCalculationTcatbalFileRepository.AccessOutcome.COMPLETED)
			throw fail("open", opened.status(), "ERROR OPENING TRANSACTION CATEGORY BALANCE");
		var openedXref = xref.openXrefFile();
		if (openedXref.outcome() != InterestCalculationXrefFileRepository.AccessOutcome.COMPLETED)
			throw fail("open", openedXref.status(), "ERROR OPENING CROSS REF FILE" + openedXref.status());
		var openedGroups = groups.openDiscgrpFile();
		if (openedGroups.outcome() != InterestCalculationDiscgrpFileRepository.AccessOutcome.COMPLETED)
			throw fail("open", openedGroups.status(), "ERROR OPENING DALY REJECTS FILE");
		var openedAccounts = accounts.openAccountFile();
		if (openedAccounts.outcome() != InterestCalculationAccountFileRepository.AccessOutcome.COMPLETED)
			throw fail("open", openedAccounts.status(), "ERROR OPENING ACCOUNT MASTER FILE");
		var openedOutput = output.openTransactFile();
		if (openedOutput.outcome() != InterestCalculationTransactFileRepository.AccessOutcome.COMPLETED)
			throw fail("open", openedOutput.status(), "ERROR OPENING TRANSACTION FILE");
	}

	@Override
	public TransactionCategoryBalance read()
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
			throw fail("read", result.status(), "ERROR READING TRANSACTION CATEGORY FILE");
		}
	}

	// End of data: the closes in source order and the closing display. The
	// last account is not rewritten: the source's branch that would rewrite
	// it sits inside PERFORM UNTIL END-OF-FILE = 'Y' and so never runs.
	private void finish()
	{
		var closed = input.closeTcatbalFile();
		if (closed.outcome() != InterestCalculationTcatbalFileRepository.AccessOutcome.COMPLETED)
			throw fail("close", closed.status(), "ERROR CLOSING TRANSACTION BALANCE FILE");
		var closedXref = xref.closeXrefFile();
		if (closedXref.outcome() != InterestCalculationXrefFileRepository.AccessOutcome.COMPLETED)
			throw fail("close", closedXref.status(), "ERROR CLOSING CROSS REF FILE");
		var closedGroups = groups.closeDiscgrpFile();
		if (closedGroups.outcome() != InterestCalculationDiscgrpFileRepository.AccessOutcome.COMPLETED)
			throw fail("close", closedGroups.status(), "ERROR CLOSING DISCLOSURE GROUP FILE");
		var closedAccounts = accounts.closeAccountFile();
		if (closedAccounts.outcome() != InterestCalculationAccountFileRepository.AccessOutcome.COMPLETED)
			throw fail("close", closedAccounts.status(), "ERROR CLOSING ACCOUNT FILE");
		var closedOutput = output.closeTransactFile();
		if (closedOutput.outcome() != InterestCalculationTransactFileRepository.AccessOutcome.COMPLETED)
			throw fail("close", closedOutput.status(), "ERROR CLOSING TRANSACTION FILE");
		log.info("END OF EXECUTION OF PROGRAM CBACT04C");
		finished = true;
	}

	@Override
	public void write(Chunk<? extends TransactionCategoryBalance> records)
	{
		for (TransactionCategoryBalance record : records)
			process(record);
	}

	private void process(TransactionCategoryBalance record)
	{
		++recordCount;
		log.info(displayImage(record));
		if (lastAccountId == null || lastAccountId != record.accountId())
		{
			if (!firstTime)
				rewriteAccount();
			else
				firstTime = false;
			totalInterest = BigDecimal.ZERO;
			lastAccountId = record.accountId();
			account = lookups.account(record.accountId()).orElseThrow(() ->
				notFound(record.accountId(), "ERROR READING ACCOUNT FILE"));
			card = lookups.cardOfAccount(record.accountId()).orElseThrow(() ->
				notFound(record.accountId(), "ERROR READING XREF FILE"));
		}
		BigDecimal rate = rateFor(record);
		if (rate.signum() != 0)
		{
			BigDecimal monthly = interest.derive(record.balance(), rate);
			totalInterest = totalInterest.add(monthly);
			writeInterest(record.accountId(), monthly);
		}
	}

	// The account's own group first, then the default group.
	private BigDecimal rateFor(TransactionCategoryBalance record)
	{
		var rate = lookups.rate(account.acctGroupId(), record.typeCode(), record.categoryCode());
		if (rate.isPresent())
			return rate.get();
		log.info("DISCLOSURE GROUP RECORD MISSING");
		log.info("TRY WITH DEFAULT GROUP CODE");
		return lookups.rate("DEFAULT", record.typeCode(), record.categoryCode())
			.orElseThrow(() -> fail("read", "23", "ERROR READING DEFAULT DISCLOSURE GROUP"));
	}

	private void writeInterest(long accountId, BigDecimal monthly)
	{
		++transactionSuffix;
		String timestamp = LocalDateTime.now().format(DB2_TIMESTAMP);
		var written = output.write(new InterestCalculationTransactFileRepository.TransactionSnapshot(
			transactionIdPrefix + String.format("%06d", transactionSuffix), "01",
			5, "System",
			"Int. for a/c " + String.format("%011d", accountId), monthly,
			0, "", "", "", card, timestamp, timestamp));
		if (written.outcome() != InterestCalculationTransactFileRepository.AccessOutcome.COMPLETED)
			throw fail("write", written.status(), "ERROR WRITING TRANSACTION RECORD");
	}

	private void rewriteAccount()
	{
		var rewritten = accounts.update(new InterestCalculationAccountFileRepository.AccountSnapshot(
			account.acctId(), account.acctActiveStatus(),
			account.acctCurrBal().add(totalInterest), account.acctCreditLimit(),
			account.acctCashCreditLimit(), account.acctOpenDate(),
			account.acctExpiraionDate(), account.acctReissueDate(),
			BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2),
			account.acctAddrZip(), account.acctGroupId()));
		if (rewritten.outcome() != InterestCalculationAccountFileRepository.AccessOutcome.COMPLETED)
			throw fail("rewrite", rewritten.status(), "ERROR RE-WRITING ACCOUNT FILE");
	}

	// The source displays the whole record: key, signed zoned balance and its
	// spare trailing bytes, which the table does not keep and so show as spaces.
	private static String displayImage(TransactionCategoryBalance record)
	{
		return String.format("%011d", record.accountId()) + record.typeCode()
			+ String.format("%04d", record.categoryCode())
			+ zoned(record.balance(), 11) + " ".repeat(22);
	}

	static String zoned(BigDecimal value, int digits)
	{
		String text = value.movePointRight(2).abs().toBigInteger().toString();
		text = "0".repeat(Math.max(0, digits - text.length())) + text;
		int last = text.charAt(text.length() - 1) - '0';
		char sign = (value.signum() < 0 ? "}JKLMNOPQR" : "{ABCDEFGHI").charAt(last);
		return text.substring(0, text.length() - 1) + sign;
	}

	private InterestAccessException notFound(long accountId, String readMessage)
	{
		log.info("ACCOUNT NOT FOUND: " + String.format("%011d", accountId));
		return fail("read", "23", readMessage);
	}

	private InterestAccessException fail(String operation, String status, String message)
	{
		log.error(message);
		log.error(statusFormatter.formatStatus(status));
		log.error("ABENDING PROGRAM");
		return new InterestAccessException(operation, status, message);
	}

	public long recordCount()
	{
		return recordCount;
	}

	public static final class InterestAccessException extends RuntimeException
	{
		private final String operation;
		private final String status;

		private InterestAccessException(String operation, String status, String message)
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

// Runs the program as a Spring Batch job, the counterpart of its JCL step,
// with the step's PARM date. Launched by name
// (--spring.batch.job.name=interestCalculationJob) or once at web
// startup with --carddemo.batch.intcalc.enabled=true, after the seed imports.
@Configuration
class InterestCalculationJobConfiguration
{
	@Bean
	InterestCalculationJob interestCalculationProgram(javax.sql.DataSource dataSource,
		Environment environment)
	{
		Charset hostCharset =
			Charset.forName("IBM037");
		var files = new InterestCalculationJobAdapters.TableFiles(dataSource);
		return new InterestCalculationJob(files.categoryBalances(), files.crossReferences(),
			files.disclosureGroups(), files.accounts(),
			new InterestCalculationJobAdapters.SystemTransactions(Path.of(
				environment.getProperty("carddemo.batch.systran.path", "systran.dat")),
				hostCharset),
			files, new InterestCalculationStatusFormatter(hostCharset),
			environment.getProperty("carddemo.batch.intcalc.parm-date", "2022071800"));
	}

	@Bean
	Job interestCalculationJob(JobRepository jobs,
		PlatformTransactionManager transactions,
		InterestCalculationJob interestCalculationProgram)
	{
		Step step = new StepBuilder("interestCalculationStep", jobs)
			.<TransactionCategoryBalance, TransactionCategoryBalance>chunk(1, transactions)
			.reader(interestCalculationProgram)
			.writer(interestCalculationProgram)
			.build();
		return new JobBuilder("interestCalculationJob", jobs)
			.listener(interestCalculationProgram)
			.start(step)
			.build();
	}

	@Bean
	@Order(2000)
	ApplicationRunner interestCalculationStartupRun(Environment environment,
		JobLauncher launcher,
		@Qualifier("interestCalculationJob") Job interestCalculationJob)
	{
		return (ApplicationArguments arguments) ->
		{
			if (environment.getProperty("carddemo.batch.intcalc.enabled", Boolean.class, false))
				launcher.run(interestCalculationJob, new JobParametersBuilder()
					.addLong("startedAt", System.currentTimeMillis()).toJobParameters());
		};
	}
}

package com.cardemo.batch;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;

// The JCL's sort step and the report program as one step. The sort keeps the
// transactions whose processing date falls in the range and orders them by
// card number. The program then writes the report: headers on the first
// detail and at every page break (each multiple of the page size, header and
// total lines included), an account total when the card changes, and at end
// of file -- with the last record still in place -- adds the last amount a
// second time, then writes the page and grand totals. The last card's account
// total is never written. Both are the source's behaviour, kept.
public final class TransactionDetailReportJob
{
	private static final Logger log = LoggerFactory.getLogger(TransactionDetailReportJob.class);
	private static final DateTimeFormatter ORIGIN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");
	private static final DateTimeFormatter PROCESSED = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS");
	private static final int WIDTH = 133;
	private static final int PAGE_SIZE = 20;
	private boolean endOfFile = false;
	private Transaction transaction;

	private final javax.sql.DataSource dataSource;
	private final Path reportPath;
	private final boolean createNew;
	private final Charset charset;
	private final String rangeStart;
	private final String rangeEnd;
	private OutputStream report;
	private long lineCounter;
	private BigDecimal pageTotal;
	private BigDecimal accountTotal;
	private BigDecimal grandTotal;

	public TransactionDetailReportJob(javax.sql.DataSource dataSource, Path reportPath,
		Charset charset, String rangeStart, String rangeEnd)
	{
		this(dataSource, reportPath, charset, rangeStart, rangeEnd, false);
	}

	private TransactionDetailReportJob(javax.sql.DataSource dataSource, Path reportPath,
		Charset charset, String rangeStart, String rangeEnd, boolean createNew)
	{
		this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
		this.reportPath = Objects.requireNonNull(reportPath, "reportPath");
		this.createNew = createNew;
		this.charset = Objects.requireNonNull(charset, "charset");
		this.rangeStart = Objects.requireNonNull(rangeStart, "rangeStart");
		this.rangeEnd = Objects.requireNonNull(rangeEnd, "rangeEnd");
	}

	// The same program over another date range, as a submitted job's date
	// parameters give it.
	public TransactionDetailReportJob forRange(String start, String end)
	{
		return new TransactionDetailReportJob(dataSource, reportPath, charset, start, end);
	}

	// Submitted jobs retain one immutable generation per actual Batch execution.
	// Startup and direct forRange callers keep their configured single output.
	public TransactionDetailReportJob forExecution(String start, String end, long executionId)
	{
		return forExecution(start, end, executionId, UUID.randomUUID());
	}

	public TransactionDetailReportJob forExecution(String start, String end, long executionId, UUID generationId)
	{
		if (executionId <= 0)
			throw new IllegalArgumentException("executionId must be positive");
		Objects.requireNonNull(generationId, "generationId");
		Path base = reportPath.toAbsolutePath().normalize();
		Path generation = base.resolveSibling(base.getFileName() + ".generation-" + generationId + ".execution-" + executionId);
		return new TransactionDetailReportJob(dataSource, generation, charset, start, end, true);
	}

	javax.sql.DataSource dataSource()
	{
		return dataSource;
	}

	public Path outputPath()
	{
		return reportPath;
	}

	public void run() throws IOException, SQLException
	{
		log.info("START OF EXECUTION OF PROGRAM CBTRN03C");
		lineCounter = 0;
		pageTotal = BigDecimal.ZERO.setScale(2);
		accountTotal = BigDecimal.ZERO.setScale(2);
		grandTotal = BigDecimal.ZERO.setScale(2);
		log.info("Reporting from " + rangeStart + " to " + rangeEnd);
		try (OutputStream out = createNew
				? Files.newOutputStream(reportPath, StandardOpenOption.CREATE_NEW,
					StandardOpenOption.WRITE)
				: Files.newOutputStream(reportPath);
			Connection connection = dataSource.getConnection();
			PreparedStatement select = connection.prepareStatement(
				"SELECT TRAN_ID, TRAN_TYPE_CD, TRAN_CAT_CD, TRAN_SOURCE, TRAN_DESC, TRAN_AMT, TRAN_MERCHANT_ID, TRAN_MERCHANT_NAME, TRAN_MERCHANT_CITY, TRAN_MERCHANT_ZIP, TRAN_CARD_NUM, TRAN_ORIG_TS, TRAN_PROC_TS FROM TRANSACT "
					+ "WHERE CAST(TRAN_PROC_TS AS DATE) BETWEEN ? AND ? "
					+ "ORDER BY TRAN_CARD_NUM, TRAN_ID"))
		{
			report = out;
			select.setObject(1, LocalDate.parse(rangeStart));
			select.setObject(2, LocalDate.parse(rangeEnd));
			boolean firstTime = true;
			String currentCard = null;
			String lastAmountText = null;
			BigDecimal lastAmount = null;
			try (ResultSet t = select.executeQuery())
			{
				while (t.next())
				{
					BigDecimal amount = t.getBigDecimal(6);
					String card = pad(t.getString(11), 16);
					log.info(pad(t.getString(1), 16) + pad(t.getString(2), 2) + String.format("%04d", t.getLong(3))
						+ pad(t.getString(4), 10) + pad(t.getString(5), 100) + zoned(amount, 11)
						+ String.format("%09d", t.getLong(7)) + pad(t.getString(8), 50) + pad(t.getString(9), 50)
						+ pad(t.getString(10), 10) + card + timestamp(t, 12, ORIGIN) + timestamp(t, 13, PROCESSED)
						+ " ".repeat(20));
					String account = null;
					if (!card.equals(currentCard))
					{
						if (!firstTime)
							accountTotals();
						currentCard = card;
					}
					account = lookup(connection, "SELECT XREF_ACCT_ID FROM CCXREF WHERE XREF_CARD_NUM = ?", card,
						"INVALID CARD NUMBER : " + card);
					String typeDescription = lookup(connection, "SELECT TR_DESCRIPTION FROM TRANSACTION_TYPE WHERE TR_TYPE = ?", t.getString(2),
						"INVALID TRANSACTION TYPE : " + t.getString(2));
					String categoryDescription = lookup(connection, "SELECT TRAN_CAT_TYPE_DESC FROM TRAN_CATG WHERE TRAN_CAT_KEY = ?",
						t.getString(2) + String.format("%04d", t.getLong(3)),
						"INVALID TRAN CATG KEY : " + t.getString(2) + String.format("%04d", t.getLong(3)));
					if (firstTime)
					{
						firstTime = false;
						headers();
					}
					if (lineCounter % PAGE_SIZE == 0)
					{
						pageTotals();
						headers();
					}
					pageTotal = pageTotal.add(amount);
					accountTotal = accountTotal.add(amount);
					write(pad(t.getString(1), 16) + " " + String.format("%011d", Long.parseLong(account.strip()))
						+ " " + pad(t.getString(2), 2) + "-" + pad(typeDescription, 15) + " "
						+ String.format("%04d", t.getLong(3)) + "-" + pad(categoryDescription, 29) + " "
						+ pad(t.getString(4), 10) + "    " + edited(amount, false) + "  ");
					++lineCounter;
					lastAmount = amount;
				}
			}
			if (lastAmount != null)
			{
				log.info("TRAN-AMT " + zoned(lastAmount, 11));
				log.info("WS-PAGE-TOTAL" + zoned(pageTotal, 11));
				pageTotal = pageTotal.add(lastAmount);
				accountTotal = accountTotal.add(lastAmount);
				pageTotals();
				write("Grand Total" + ".".repeat(86) + edited(grandTotal, true));
			}
		}
		log.info("END OF EXECUTION OF PROGRAM CBTRN03C");
	}

	// The lookups the source reads by key; a missing key ends the run.
	private String lookup(Connection connection, String sql, String key, String missing) throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement(sql))
		{
			statement.setString(1, key);
			try (ResultSet row = statement.executeQuery())
			{
				if (row.next())
					return row.getString(1);
			}
		}
		log.error(missing);
		log.error("ABENDING PROGRAM");
		throw new IllegalStateException(missing);
	}

	private void headers() throws IOException
	{
		write(pad("DALYREPT", 38) + pad("Daily Transaction Report", 41) + "Date Range: " + rangeStart
			+ " to " + rangeEnd);
		write("");
		write(pad("Transaction ID", 17) + pad("Account ID", 12) + pad("Transaction Type", 19)
			+ pad("Tran Category", 35) + pad("Tran Source", 14) + " " + pad("        Amount", 16));
		write("-".repeat(WIDTH));
		lineCounter += 4;
	}

	private void pageTotals() throws IOException
	{
		write("Page Total " + ".".repeat(86) + edited(pageTotal, true));
		grandTotal = grandTotal.add(pageTotal);
		pageTotal = BigDecimal.ZERO.setScale(2);
		++lineCounter;
		write("-".repeat(WIDTH));
		++lineCounter;
	}

	private void accountTotals() throws IOException
	{
		write("Account Total" + ".".repeat(84) + edited(accountTotal, true));
		accountTotal = BigDecimal.ZERO.setScale(2);
		++lineCounter;
		write("-".repeat(WIDTH));
		++lineCounter;
	}

	private void write(String line) throws IOException
	{
		report.write(pad(line, WIDTH).getBytes(charset));
	}

	private static String timestamp(ResultSet r, int column, DateTimeFormatter format) throws SQLException
	{
		LocalDateTime value = r.getObject(column, LocalDateTime.class);
		return value == null ? " ".repeat(26) : value.format(format);
	}

	// The edit pictures -ZZZ,ZZZ,ZZZ.ZZ and +ZZZ,ZZZ,ZZZ.ZZ: a fixed sign
	// (minus shows only when negative, plus shows + or -), leading zeros and
	// their commas suppressed, the whole field blank when the value is zero.
	static String edited(BigDecimal value, boolean plus)
	{
		if (value.signum() == 0)
			return " ".repeat(15);
		BigDecimal abs = value.abs().setScale(2);
		String whole = abs.toBigInteger().signum() == 0 ? ""
			: String.format("%,d", abs.toBigInteger());
		String fraction = abs.toPlainString().substring(abs.toPlainString().length() - 2);
		String sign = value.signum() < 0 ? "-" : plus ? "+" : " ";
		return sign + " ".repeat(Math.max(0, 11 - whole.length())) + whole + "." + fraction;
	}

	static String zoned(BigDecimal value, int digits)
	{
		String text = value.movePointRight(2).abs().toBigInteger().toString();
		text = "0".repeat(Math.max(0, digits - text.length())) + text;
		int last = text.charAt(text.length() - 1) - '0';
		char sign = (value.signum() < 0 ? "}JKLMNOPQR" : "{ABCDEFGHI").charAt(last);
		return text.substring(0, text.length() - 1) + sign;
	}

	private static String pad(String value, int width)
	{
		String text = value == null ? "" : value;
		return text.length() >= width ? text.substring(0, width) : text + " ".repeat(width - text.length());
	}
}

// Runs the report as a Spring Batch job (one tasklet step), the counterpart
// of the TRANREPT JCL, launched by name
// (--spring.batch.job.name=transactionDetailReportJob) with the range at
// --carddemo.batch.tranrept.start-date and --carddemo.batch.tranrept.end-date; or once at startup
// with --carddemo.batch.tranrept.enabled=true, after the jobs that run before it.
@Configuration
class TransactionDetailReportJobConfiguration
{
	@Bean
	TransactionDetailReportJob transactionDetailReportProgram(javax.sql.DataSource dataSource,
		Environment environment)
	{
		return new TransactionDetailReportJob(dataSource, Path.of(environment.getProperty(
			"carddemo.batch.tranrept.output", "tranrept.dat")),
			Charset.forName("IBM037"),
			environment.getProperty("carddemo.batch.tranrept.start-date", "2022-01-01"),
			environment.getProperty("carddemo.batch.tranrept.end-date", "2022-07-06"));
	}

	@Bean
	Job transactionDetailReportJob(JobRepository jobs,
		PlatformTransactionManager transactions,
		TransactionDetailReportJob transactionDetailReportProgram)
	{
		Step step = new StepBuilder("transactionDetailReportStep", jobs)
			.tasklet((contribution, context) ->
			{
				Map<String, Object> parameters = context.getStepContext().getJobParameters();
				if (parameters.containsKey("startDate") && parameters.containsKey("endDate"))
				{
					org.springframework.batch.core.JobExecution execution = contribution.getStepExecution().getJobExecution();
					// Repository IDs may restart (for example with the default in-memory
					// database), but files outlive that repository. Persist an attempt
					// UUID before reserving its immutable path; never overwrite a prior file.
					UUID generationId;
					if (execution.getExecutionContext().containsKey("reportGenerationId"))
						generationId = UUID.fromString(execution.getExecutionContext().getString("reportGenerationId"));
					else
					{
						generationId = UUID.randomUUID();
						execution.getExecutionContext().putString("reportGenerationId", generationId.toString());
						jobs.updateExecutionContext(execution);
					}
					TransactionDetailReportJob generation = transactionDetailReportProgram.forExecution((String) parameters.get("startDate"),
						(String) parameters.get("endDate"), execution.getId(), generationId);
					generation.run();
					execution.getExecutionContext().putString("reportOutputPath", generation.outputPath().toString());
					jobs.updateExecutionContext(execution);
				}
				else
					transactionDetailReportProgram.run();
				return RepeatStatus.FINISHED;
			}, transactions)
			.build();
		return new JobBuilder("transactionDetailReportJob", jobs)
			.listener(new org.springframework.batch.core.JobExecutionListener()
			{
				@Override
				public void beforeJob(org.springframework.batch.core.JobExecution execution)
				{
					String submission = execution.getJobParameters().getString("submissionId");
					if (submission != null)
					{
						int linked = new org.springframework.jdbc.core.JdbcTemplate(transactionDetailReportProgram.dataSource())
							.update("UPDATE CARDEMO_REPORT_SUBMISSION SET EXECUTION_ID = ?, STATE = 'EXECUTING' "
								+ "WHERE SUBMISSION_ID = ? AND STATE = 'CLAIMED' AND EXECUTION_ID IS NULL", execution.getId(), submission);
						if (linked != 1) throw new IllegalStateException("Report submission could not be linked to execution");
					}
				}
			})
			.start(step).build();
	}

	@Bean
	@Order(4000)
	ApplicationRunner transactionDetailReportStartupRun(Environment environment,
		JobLauncher launcher,
		@Qualifier("transactionDetailReportJob") Job transactionDetailReportJob)
	{
		return (ApplicationArguments arguments) ->
		{
			if (environment.getProperty("carddemo.batch.tranrept.enabled", Boolean.class, false))
				launcher.run(transactionDetailReportJob, new JobParametersBuilder()
					.addLong("startedAt", System.currentTimeMillis()).toJobParameters());
		};
	}
}

package com.cardemo.batch.interest;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
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

// COMBTRAN, a JCL utility job with no COBOL program: DFSORT sorts the
// transaction file's backup and the system transactions file by transaction
// id, and IDCAMS REPRO loads the result into the transaction file. Here the
// backup is the table itself, so the job merges the system transactions file
// into it by key. (REPRO without REPLACE would report each record already in
// the file as a duplicate; the merge is the step's intent.)
@Configuration
class InterestCalculationJobCombineTransactions
{
	private static final DateTimeFormatter DB2 = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS");
	private static final DateTimeFormatter TEXT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

	@Bean
	Job combineTransactionsJob(JobRepository jobs, PlatformTransactionManager transactions,
		javax.sql.DataSource dataSource, Environment environment)
	{
		Step step = new StepBuilder("combineTransactionsJobStep", jobs)
			.tasklet((contribution, context) ->
			{
				combine(dataSource, Path.of(environment.getProperty(
					"carddemo.batch.systran.path", "systran.dat")),
					Charset.forName("IBM037"));
				return RepeatStatus.FINISHED;
			}, transactions)
			.build();
		return new JobBuilder("combineTransactionsJob", jobs).start(step).build();
	}

	@Bean
	@Order(2500)
	ApplicationRunner combineTransactionsJobStartupRun(Environment environment, JobLauncher launcher,
		@Qualifier("combineTransactionsJob") Job job)
	{
		return (ApplicationArguments arguments) ->
		{
			if (environment.getProperty("carddemo.batch.combtran.enabled", Boolean.class, false))
				launcher.run(job, new JobParametersBuilder()
					.addLong("startedAt", System.currentTimeMillis()).toJobParameters());
		};
	}

	static int combine(javax.sql.DataSource dataSource, Path systemTransactions,
		Charset charset) throws IOException, SQLException
	{
		int loaded = 0;
		try (InputStream input = Files.newInputStream(systemTransactions);
			Connection connection = dataSource.getConnection();
			PreparedStatement statement = connection.prepareStatement(
				"MERGE INTO TRANSACT (TRAN_ID, TRAN_TYPE_CD, TRAN_CAT_CD, TRAN_SOURCE, TRAN_DESC, TRAN_AMT, TRAN_MERCHANT_ID, TRAN_MERCHANT_NAME, TRAN_MERCHANT_CITY, TRAN_MERCHANT_ZIP, TRAN_CARD_NUM, TRAN_ORIG_TS, TRAN_PROC_TS) KEY(TRAN_ID) "
					+ "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"))
		{
			while (true)
			{
				byte[] bytes = input.readNBytes(350);
				if (bytes.length < 350) break;
				String r = new String(bytes, charset);
				statement.setString(1, r.substring(0, 16));
				statement.setString(2, r.substring(16, 18));
				statement.setLong(3, Long.parseLong(r.substring(18, 22)));
				statement.setString(4, r.substring(22, 32).strip());
				statement.setString(5, r.substring(32, 132).strip());
				statement.setBigDecimal(6, zoned(r.substring(132, 143)));
				statement.setLong(7, Long.parseLong(r.substring(143, 152)));
				statement.setString(8, r.substring(152, 202).strip());
				statement.setString(9, r.substring(202, 252).strip());
				statement.setString(10, r.substring(252, 262).strip());
				statement.setString(11, r.substring(262, 278));
				statement.setObject(12, timestamp(r.substring(278, 304)));
				statement.setObject(13, timestamp(r.substring(304, 330)));
				statement.executeUpdate();
				++loaded;
			}
		}
		return loaded;
	}

	// A signed zoned decimal with two decimal places, the sign overpunched on
	// the last digit.
	private static BigDecimal zoned(String text)
	{
		char last = text.charAt(text.length() - 1);
		int positive = "{ABCDEFGHI".indexOf(last), negative = "}JKLMNOPQR".indexOf(last);
		String digits = text.substring(0, text.length() - 1)
			+ (positive >= 0 ? positive : negative >= 0 ? negative : last - '0');
		BigDecimal value = new BigDecimal(digits).movePointLeft(2);
		return negative >= 0 ? value.negate() : value;
	}

	// The system transactions carry DB2-format times; the daily file's text
	// format is accepted too.
	private static LocalDateTime timestamp(String text)
	{
		String value = text.strip();
		if (value.isEmpty()) return null;
		return LocalDateTime.parse(value, value.charAt(10) == '-' ? DB2 : TEXT);
	}
}

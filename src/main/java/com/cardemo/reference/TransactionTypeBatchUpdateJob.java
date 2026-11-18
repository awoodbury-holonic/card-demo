package com.cardemo.reference;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;

// The source's main line as one chunk-oriented step over the maintenance
// input: each record is displayed, then added, updated, deleted or ignored
// in the table. An error is the source's 9999 paragraph: it displays the
// message field, sets return code 4 and carries on. That field is never
// cleared, so a shorter message keeps the tail of a longer earlier one.
public final class TransactionTypeBatchUpdateJob
	implements ItemReader<String>, ItemWriter<String>, JobExecutionListener, ExitCodeGenerator
{
	private static final Logger log = LoggerFactory.getLogger(TransactionTypeBatchUpdateJob.class);
	private static final int MESSAGE_WIDTH = 80;

	private final javax.sql.DataSource dataSource;
	private final Path inputPath;
	private final Charset charset;
	private InputStream input;
	private String message;
	private int returnCode;

	public TransactionTypeBatchUpdateJob(javax.sql.DataSource dataSource, Path inputPath,
		Charset charset)
	{
		this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
		this.inputPath = Objects.requireNonNull(inputPath, "inputPath");
		this.charset = Objects.requireNonNull(charset, "charset");
	}

	// The source displays whether the open worked and carries on either way.
	// Here an input that cannot be opened reads as empty.
	@Override
	public void beforeJob(JobExecution execution)
	{
		message = " ".repeat(MESSAGE_WIDTH);
		returnCode = 0;
		try
		{
			input = Files.newInputStream(inputPath);
			log.info("OPEN FILE OK");
		}
		catch (IOException failure)
		{
			input = null;
			log.info("OPEN FILE NOT OK");
		}
	}

	@Override
	public String read() throws IOException
	{
		if (input == null)
			return null;
		byte[] bytes = input.readNBytes(TransactionTypeChangeInput.RECORD_LENGTH);
		if (bytes.length < TransactionTypeChangeInput.RECORD_LENGTH)
		{
			input.close();
			input = null;
			return null;
		}
		return new String(bytes, charset);
	}

	@Override
	public void write(Chunk<? extends String> images) throws IOException
	{
		for (String image : images)
		{
			log.info("PROCESSING   " + image);
			apply(TransactionTypeChangeInput.decode(image));
		}
	}

	private void apply(TransactionTypeChangeInput record)
	{
		switch (record.inputRecType())
		{
		case "A" ->
		{
			log.info("ADDING RECORD");
			change("INSERT INTO TRANSACTION_TYPE (TR_TYPE, TR_DESCRIPTION) VALUES (?, ?)",
				record.inputRecNumber(), record.inputRecDesc(), "RECORD INSERTED SUCCESSFULLY", false);
		}
		case "U" ->
		{
			log.info("UPDATING RECORD");
			change("UPDATE TRANSACTION_TYPE SET TR_DESCRIPTION = ? WHERE TR_TYPE = ?",
				record.inputRecDesc(), record.inputRecNumber(), "RECORD UPDATED SUCCESSFULLY", true);
		}
		case "D" ->
		{
			log.info("DELETING RECORD");
			change("DELETE FROM TRANSACTION_TYPE WHERE TR_TYPE = ?",
				record.inputRecNumber(), null, "RECORD DELETED SUCCESSFULLY", true);
		}
		case "*" -> log.info("IGNORING COMMENTED LINE");
		default -> fail("ERROR: TYPE NOT VALID");
		}
	}

	// One SQL statement and the source's reading of its SQLCODE: zero is
	// success, +100 (no row) is "not found" for update and delete, and a
	// failure shows its DB2 code (a duplicate key is -803).
	private void change(String sql, String leading, String trailing, String success,
		boolean missingIsError)
	{
		try (Connection connection = dataSource.getConnection();
			PreparedStatement statement = connection.prepareStatement(sql))
		{
			statement.setString(1, leading);
			if (trailing != null)
				statement.setString(2, trailing);
			if (statement.executeUpdate() == 0 && missingIsError)
				fail("No records found.");
			else
				log.info(success);
		}
		catch (SQLException failure)
		{
			int sqlCode = "23505".equals(failure.getSQLState()) ? -803 : -1;
			fail("Error accessing: TRANSACTION_TYPE table. SQLCODE:" + String.format("%5d", sqlCode));
		}
	}

	private void fail(String text)
	{
		message = (text + message.substring(Math.min(text.length(), MESSAGE_WIDTH)))
			.substring(0, MESSAGE_WIDTH);
		log.info(message);
		returnCode = 4;
	}

	@Override
	public int getExitCode()
	{
		return returnCode;
	}
}

// Runs the program as a Spring Batch job, the counterpart of its JCL step,
// launched by name (--spring.batch.job.name=transactionTypeBatchUpdateJob)
// with the maintenance input at --carddemo.batch.mnttrdb2.input.
@Configuration
class TransactionTypeBatchUpdateJobConfiguration
{
	@Bean
	TransactionTypeBatchUpdateJob transactionTypeBatchUpdateProgram(javax.sql.DataSource dataSource,
		Environment environment)
	{
		return new TransactionTypeBatchUpdateJob(dataSource, Path.of(environment.getProperty(
			"carddemo.batch.mnttrdb2.input", "INPFILE")),
			Charset.forName("IBM037"));
	}

	@Bean
	Job transactionTypeBatchUpdateJob(JobRepository jobs,
		PlatformTransactionManager transactions,
		TransactionTypeBatchUpdateJob transactionTypeBatchUpdateProgram)
	{
		Step step = new StepBuilder("transactionTypeBatchUpdateStep", jobs)
			.<String, String>chunk(1, transactions)
			.reader(transactionTypeBatchUpdateProgram)
			.writer(transactionTypeBatchUpdateProgram)
			.build();
		return new JobBuilder("transactionTypeBatchUpdateJob", jobs)
			.listener(transactionTypeBatchUpdateProgram)
			.start(step)
			.build();
	}

	// Spring Boot adds its failed-job exit code only when no other exit code
	// generator exists; the program's return code is one, so keep Spring's.
	@Bean
	org.springframework.boot.autoconfigure.batch.JobExecutionExitCodeGenerator
		transactionTypeBatchUpdateFailedJobExitCode()
	{
		return new org.springframework.boot.autoconfigure.batch.JobExecutionExitCodeGenerator();
	}
}

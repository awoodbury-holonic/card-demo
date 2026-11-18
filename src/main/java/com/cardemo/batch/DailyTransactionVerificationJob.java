package com.cardemo.batch;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

// The source's main line as one chunk-oriented step: each daily transaction
// is displayed, then its card is verified in the cross-reference and its
// account in the account file. At end of file the source's lookups run once
// more on the last record (they follow the inner end-of-file test, and READ
// INTO leaves the last record in place); kept.
public final class DailyTransactionVerificationJob
	implements ItemReader<String>, ItemWriter<String>, JobExecutionListener
{
	private static final Logger log = LoggerFactory.getLogger(DailyTransactionVerificationJob.class);
	private boolean endOfDailyTransFile = false;


	public record CrossReference(String cardNumber, long customerId, long accountId) { }

	// The keyed reads the generated ports leave out.
	public interface Lookups
	{
		Optional<CrossReference> crossReference(String cardNumber);
		boolean accountExists(long accountId);
	}

	// The daily transaction file: packaged fixed-width host records, in order.
	public interface RecordImages
	{
		String open();
		String next();
		String close();
	}

	private final RecordImages input;
	private final Lookups lookups;
	private final List<DailyTransactionVerificationJobAdapters.TableFile> tables;
	private final DailyTransactionVerificationStatusFormatter statusFormatter;
	private String lastImage;
	private boolean finished;

	public DailyTransactionVerificationJob(RecordImages input, Lookups lookups,
		List<DailyTransactionVerificationJobAdapters.TableFile> tables,
		DailyTransactionVerificationStatusFormatter statusFormatter)
	{
		this.input = Objects.requireNonNull(input, "input");
		this.lookups = Objects.requireNonNull(lookups, "lookups");
		this.tables = List.copyOf(tables);
		this.statusFormatter = Objects.requireNonNull(statusFormatter, "statusFormatter");
	}

	@Override
	public void beforeJob(JobExecution execution)
	{
		lastImage = null;
		finished = false;
		log.info("START OF EXECUTION OF PROGRAM CBTRN01C");
		String status = input.open();
		if (!"00".equals(status))
			throw fail(status, "ERROR OPENING DAILY TRANSACTION FILE");
		for (var table : tables)
		{
			status = table.open();
			if (!"00".equals(status))
				throw fail(status, table.openMessage());
		}
	}

	@Override
	public String read()
	{
		if (finished)
			return null;
		String image = input.next();
		if (image == null)
		{
			if (lastImage != null)
				verify(lastImage);
			finish();
			return null;
		}
		if (image.isEmpty())
			throw fail("04", "ERROR READING DAILY TRANSACTION FILE");
		return image;
	}

	private void finish()
	{
		String status = input.close();
		if (!"00".equals(status))
			throw fail(status, "ERROR CLOSING CUSTOMER FILE");
		for (var table : tables)
		{
			status = table.close();
			if (!"00".equals(status))
				throw fail(status, table.closeMessage());
		}
		log.info("END OF EXECUTION OF PROGRAM CBTRN01C");
		finished = true;
	}

	@Override
	public void write(Chunk<? extends String> images)
	{
		for (String image : images)
		{
			log.info(image);
			lastImage = image;
			verify(image);
		}
	}

	private void verify(String image)
	{
		String card = image.substring(262, 262 + 16);
		var reference = lookups.crossReference(card);
		if (reference.isEmpty())
		{
			log.info("INVALID CARD NUMBER FOR XREF");
			log.info("CARD NUMBER " + card + " COULD NOT BE VERIFIED. SKIPPING TRANSACTION ID-"
				+ image.substring(0, 0 + 16));
			return;
		}
		log.info("SUCCESSFUL READ OF XREF");
		log.info("CARD NUMBER: " + reference.get().cardNumber());
		log.info("ACCOUNT ID : " + String.format("%011d", reference.get().accountId()));
		log.info("CUSTOMER ID: " + String.format("%09d", reference.get().customerId()));
		if (lookups.accountExists(reference.get().accountId()))
			log.info("SUCCESSFUL READ OF ACCOUNT FILE");
		else
		{
			log.info("INVALID ACCOUNT NUMBER FOUND");
			log.info("ACCOUNT " + String.format("%011d", reference.get().accountId())
				+ " NOT FOUND");
		}
	}

	private VerificationAccessException fail(String status, String message)
	{
		log.error(message);
		log.error(statusFormatter.formatStatus(status));
		log.error("ABENDING PROGRAM");
		return new VerificationAccessException(status, message);
	}

	public static final class VerificationAccessException extends RuntimeException
	{
		private final String status;

		private VerificationAccessException(String status, String message)
		{
			super(message);
			this.status = status;
		}

		public String status()
		{
			return status;
		}
	}

	// The source ends a failed run with user abend 999; kept as diagnostic data.
	public static final int ABEND_CODE = 999;
}

// Runs the program as a Spring Batch job, launched by name
// (--spring.batch.job.name=dailyTransactionVerificationJob).
@Configuration
class DailyTransactionVerificationJobConfiguration
{
	@Bean
	DailyTransactionVerificationJob dailyTransactionVerificationProgram(javax.sql.DataSource dataSource)
	{
		Charset hostCharset =
			Charset.forName("IBM037");
		return new DailyTransactionVerificationJob(
			new DailyTransactionVerificationJobAdapters.SeedImages("seed-data/AWS.M2.CARDDEMO.DALYTRAN.PS", 350, hostCharset),
			new DailyTransactionVerificationJobAdapters.Lookups(dataSource),
			DailyTransactionVerificationJobAdapters.tables(dataSource),
			new DailyTransactionVerificationStatusFormatter(hostCharset));
	}

	@Bean
	Job dailyTransactionVerificationJob(JobRepository jobs,
		PlatformTransactionManager transactions,
		DailyTransactionVerificationJob dailyTransactionVerificationProgram)
	{
		Step step = new StepBuilder("dailyTransactionVerificationStep", jobs)
			.<String, String>chunk(1, transactions)
			.reader(dailyTransactionVerificationProgram)
			.writer(dailyTransactionVerificationProgram)
			.build();
		return new JobBuilder("dailyTransactionVerificationJob", jobs)
			.listener(dailyTransactionVerificationProgram)
			.start(step)
			.build();
	}
}

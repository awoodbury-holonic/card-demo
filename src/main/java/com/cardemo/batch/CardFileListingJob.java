package com.cardemo.batch;

import java.nio.charset.Charset;
import java.nio.file.Path;

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

public final class CardFileListingJob
	implements ItemReader<String>, ItemWriter<String>, JobExecutionListener
{
	private static final Logger log = LoggerFactory.getLogger(CardFileListingJob.class);
	// The source's main line as one chunk-oriented step. Before the step: its
	// opening statements and the file open. Each read is one item, which the
	// writer displays. EOF closes the file inside the step, so a close failure
	// fails the step and job too. A failed read never reaches the close.
	private boolean finished;

	@Override
	public void beforeJob(JobExecution execution)
	{
		finished = false;
		LoggerFactory.getLogger(CardFileListingJob.class).info(String.valueOf("START OF EXECUTION OF PROGRAM CBACT02C"));
		var openResult = cardfileFileRepository.openCardfileFile();
		if (openResult.outcome() != CardFileListingCardfileFileRepository.AccessOutcome.COMPLETED) {
			throw cardfileFileAbend("open", openResult.status(),
				"ERROR OPENING CARDFILE", "ABENDING PROGRAM");
		}
	}

	@Override
	public String read()
	{
		if (finished)
			return null;
		String image = readRecordImage();
		if (image != null)
			return image;
		var closeResult = cardfileFileRepository.close();
		if (closeResult.outcome() != CardFileListingCardfileFileRepository.AccessOutcome.COMPLETED) {
			throw cardfileFileAbend("close", closeResult.status(),
				"ERROR CLOSING CARDFILE", "ABENDING PROGRAM");
		}
		LoggerFactory.getLogger(CardFileListingJob.class).info(String.valueOf("END OF EXECUTION OF PROGRAM CBACT02C"));
		finished = true;
		return null;
	}

	private String readRecordImage()
	{
		var readResult = cardfileFileRepository.readNextImage();
		switch (readResult.outcome()) {
		case RECORD:
			return readResult.recordImage();
		case END_OF_DATA:
			return null;
		default:
			throw cardfileFileAbend("read", readResult.status(),
				"ERROR READING CARDFILE", "ABENDING PROGRAM");
		}
	}

	@Override
	public void write(Chunk<? extends String> records)
	{
		for (String recordImage : records)
			display(recordImage);
	}

	private void display(String recordImage)
	{
		log.info(recordImage);
	}
	private final CardFileListingCardfileFileRepository cardfileFileRepository;
	private final CardFileListingStatusFormatter statusFormatter;

	public CardFileListingJob(CardFileListingCardfileFileRepository cardfileFileRepository,
		CardFileListingStatusFormatter statusFormatter)
	{
		this.cardfileFileRepository = cardfileFileRepository;
		this.statusFormatter = statusFormatter;
	}
	// The source's abend path: it displays the failing operation's message,
	// the file status and its abend message, then terminates the program.
	// Termination is the returned exception, which the caller throws.
	private CardfileFileAccessException cardfileFileAbend(
		String operation, String status, String message, String abendMessage)
	{
		log.error(message);
		log.error(statusFormatter.formatStatus(status));
		log.error(abendMessage);
		return new CardfileFileAccessException(operation, status, message);
	}

	// A failed file operation that ends the program. The source's file status
	// is kept for diagnosis; it does not drive control flow.
	public static final class CardfileFileAccessException extends RuntimeException
	{
		private final String operation;
		private final String status;

		private CardfileFileAccessException(String operation, String status,
			String message)
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
	// The source ends a failed run with user abend 999 (CEE3ABD with
	// timing 0). Keep that source code as diagnostic data.
	// The batch launcher reports failure with a portable nonzero process status.
	public static final int ABEND_CODE = 999;
}

// Runs the program as a Spring Batch job, the counterpart of its JCL step. It
// does not run at startup; it is launched by name, for example with
// --spring.batch.job.enabled=true --spring.batch.job.name=cardFileListingJob
@Configuration
class CardFileListingJobConfiguration
{
	// The repository reads the packaged seed file; file statuses are decoded
	// in the source's host charset.
	@Bean
	CardFileListingJob cardFileListingProgram()
	{
		return new CardFileListingJob(
			CardFileListingCardfileFileRepository.local(Path.of(".")),
			new CardFileListingStatusFormatter(
				Charset.forName("IBM037")));
	}

	// One record per chunk: each record is displayed before the next read,
	// so a read failure follows exactly the records the source displayed.
	@Bean
	Job cardFileListingJob(JobRepository jobs,
		PlatformTransactionManager transactions,
		CardFileListingJob cardFileListingProgram)
	{
		Step step = new StepBuilder("cardFileListingStep", jobs)
			.<String, String>chunk(1, transactions)
			.reader(cardFileListingProgram)
			.writer(cardFileListingProgram)
			.build();
		return new JobBuilder("cardFileListingJob", jobs)
			.listener(cardFileListingProgram)
			.start(step)
			.build();
	}
}

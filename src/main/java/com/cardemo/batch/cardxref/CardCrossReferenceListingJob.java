package com.cardemo.batch.cardxref;

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

public final class CardCrossReferenceListingJob
	implements ItemReader<String>, ItemWriter<String>, JobExecutionListener
{
	private static final Logger log = LoggerFactory.getLogger(CardCrossReferenceListingJob.class);
	// The source's main line as one chunk-oriented step. Before the step: its
	// opening statements and the file open. Each read is one item, which the
	// writer displays. EOF closes the file inside the step, so a close failure
	// fails the step and job too. A failed read never reaches the close.
	private boolean finished;

	@Override
	public void beforeJob(JobExecution execution)
	{
		finished = false;
		LoggerFactory.getLogger(CardCrossReferenceListingJob.class).info(String.valueOf("START OF EXECUTION OF PROGRAM CBACT03C"));
		var openResult = xreffileFileRepository.openXreffileFile();
		if (openResult.outcome() != CardCrossReferenceListingXreffileFileRepository.AccessOutcome.COMPLETED) {
			throw xreffileFileAbend("open", openResult.status(),
				"ERROR OPENING XREFFILE", "ABENDING PROGRAM");
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
		var closeResult = xreffileFileRepository.close();
		if (closeResult.outcome() != CardCrossReferenceListingXreffileFileRepository.AccessOutcome.COMPLETED) {
			throw xreffileFileAbend("close", closeResult.status(),
				"ERROR CLOSING XREFFILE", "ABENDING PROGRAM");
		}
		LoggerFactory.getLogger(CardCrossReferenceListingJob.class).info(String.valueOf("END OF EXECUTION OF PROGRAM CBACT03C"));
		finished = true;
		return null;
	}

	private String readRecordImage()
	{
		var readResult = xreffileFileRepository.readNextImage();
		switch (readResult.outcome()) {
		case RECORD:
			return readResult.recordImage();
		case END_OF_DATA:
			return null;
		default:
			throw xreffileFileAbend("read", readResult.status(),
				"ERROR READING XREFFILE", "ABENDING PROGRAM");
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
	private final CardCrossReferenceListingXreffileFileRepository xreffileFileRepository;
	private final CardCrossReferenceListingStatusFormatter statusFormatter;

	public CardCrossReferenceListingJob(CardCrossReferenceListingXreffileFileRepository xreffileFileRepository,
		CardCrossReferenceListingStatusFormatter statusFormatter)
	{
		this.xreffileFileRepository = xreffileFileRepository;
		this.statusFormatter = statusFormatter;
	}
	// The source's abend path: it displays the failing operation's message,
	// the file status and its abend message, then terminates the program.
	// Termination is the returned exception, which the caller throws.
	private XreffileFileAccessException xreffileFileAbend(
		String operation, String status, String message, String abendMessage)
	{
		log.error(message);
		log.error(statusFormatter.formatStatus(status));
		log.error(abendMessage);
		return new XreffileFileAccessException(operation, status, message);
	}

	// A failed file operation that ends the program. The source's file status
	// is kept for diagnosis; it does not drive control flow.
	public static final class XreffileFileAccessException extends RuntimeException
	{
		private final String operation;
		private final String status;

		private XreffileFileAccessException(String operation, String status,
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
// --spring.batch.job.enabled=true --spring.batch.job.name=cardCrossReferenceListingJob
@Configuration
class CardCrossReferenceListingJobConfiguration
{
	// The repository reads the packaged seed file; file statuses are decoded
	// in the source's host charset.
	@Bean
	CardCrossReferenceListingJob cardCrossReferenceListingProgram()
	{
		return new CardCrossReferenceListingJob(
			CardCrossReferenceListingXreffileFileRepository.local(Path.of(".")),
			new CardCrossReferenceListingStatusFormatter(
				Charset.forName("IBM037")));
	}

	// One record per chunk: each record is displayed before the next read,
	// so a read failure follows exactly the records the source displayed.
	@Bean
	Job cardCrossReferenceListingJob(JobRepository jobs,
		PlatformTransactionManager transactions,
		CardCrossReferenceListingJob cardCrossReferenceListingProgram)
	{
		Step step = new StepBuilder("cardCrossReferenceListingStep", jobs)
			.<String, String>chunk(1, transactions)
			.reader(cardCrossReferenceListingProgram)
			.writer(cardCrossReferenceListingProgram)
			.build();
		return new JobBuilder("cardCrossReferenceListingJob", jobs)
			.listener(cardCrossReferenceListingProgram)
			.start(step)
			.build();
	}
}

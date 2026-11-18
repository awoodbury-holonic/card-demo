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

public final class CustomerFileListingJob
	implements ItemReader<String>, ItemWriter<String>, JobExecutionListener
{
	private static final Logger log = LoggerFactory.getLogger(CustomerFileListingJob.class);
	// The source's main line as one chunk-oriented step. Before the step: its
	// opening statements and the file open. Each read is one item, which the
	// writer displays. EOF closes the file inside the step, so a close failure
	// fails the step and job too. A failed read never reaches the close.
	private boolean finished;

	@Override
	public void beforeJob(JobExecution execution)
	{
		finished = false;
		LoggerFactory.getLogger(CustomerFileListingJob.class).info(String.valueOf("START OF EXECUTION OF PROGRAM CBCUS01C"));
		var openResult = custfileFileRepository.openCustfileFile();
		if (openResult.outcome() != CustomerFileListingCustfileFileRepository.AccessOutcome.COMPLETED) {
			throw custfileFileAbend("open", openResult.status(),
				"ERROR OPENING CUSTFILE", "ABENDING PROGRAM");
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
		var closeResult = custfileFileRepository.close();
		if (closeResult.outcome() != CustomerFileListingCustfileFileRepository.AccessOutcome.COMPLETED) {
			throw custfileFileAbend("close", closeResult.status(),
				"ERROR CLOSING CUSTOMER FILE", "ABENDING PROGRAM");
		}
		LoggerFactory.getLogger(CustomerFileListingJob.class).info(String.valueOf("END OF EXECUTION OF PROGRAM CBCUS01C"));
		finished = true;
		return null;
	}

	private String readRecordImage()
	{
		var readResult = custfileFileRepository.readNextImage();
		switch (readResult.outcome()) {
		case RECORD:
			return readResult.recordImage();
		case END_OF_DATA:
			return null;
		default:
			throw custfileFileAbend("read", readResult.status(),
				"ERROR READING CUSTOMER FILE", "ABENDING PROGRAM");
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
	private final CustomerFileListingCustfileFileRepository custfileFileRepository;
	private final CustomerFileListingStatusFormatter statusFormatter;

	public CustomerFileListingJob(CustomerFileListingCustfileFileRepository custfileFileRepository,
		CustomerFileListingStatusFormatter statusFormatter)
	{
		this.custfileFileRepository = custfileFileRepository;
		this.statusFormatter = statusFormatter;
	}
	// The source's abend path: it displays the failing operation's message,
	// the file status and its abend message, then terminates the program.
	// Termination is the returned exception, which the caller throws.
	private CustfileFileAccessException custfileFileAbend(
		String operation, String status, String message, String abendMessage)
	{
		log.error(message);
		log.error(statusFormatter.formatStatus(status));
		log.error(abendMessage);
		return new CustfileFileAccessException(operation, status, message);
	}

	// A failed file operation that ends the program. The source's file status
	// is kept for diagnosis; it does not drive control flow.
	public static final class CustfileFileAccessException extends RuntimeException
	{
		private final String operation;
		private final String status;

		private CustfileFileAccessException(String operation, String status,
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
// --spring.batch.job.enabled=true --spring.batch.job.name=customerFileListingJob
@Configuration
class CustomerFileListingJobConfiguration
{
	// The repository reads the packaged seed file; file statuses are decoded
	// in the source's host charset.
	@Bean
	CustomerFileListingJob customerFileListingProgram()
	{
		return new CustomerFileListingJob(
			CustomerFileListingCustfileFileRepository.local(Path.of(".")),
			new CustomerFileListingStatusFormatter(
				Charset.forName("IBM037")));
	}

	// One record per chunk: each record is displayed before the next read,
	// so a read failure follows exactly the records the source displayed.
	@Bean
	Job customerFileListingJob(JobRepository jobs,
		PlatformTransactionManager transactions,
		CustomerFileListingJob customerFileListingProgram)
	{
		Step step = new StepBuilder("customerFileListingStep", jobs)
			.<String, String>chunk(1, transactions)
			.reader(customerFileListingProgram)
			.writer(customerFileListingProgram)
			.build();
		return new JobBuilder("customerFileListingJob", jobs)
			.listener(customerFileListingProgram)
			.start(step)
			.build();
	}
}

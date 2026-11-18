package com.cardemo.batch;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

import com.cardemo.authorization.PendingAuthorizationDatabase;

// The load: each root in the roots file is inserted, then each child in the
// children file under the root its key names; a segment already present is
// reported and kept. Kept from the source: a child whose root is missing is
// skipped without a message (the source's failure check is nested in its
// success branch), and a child whose key is not numeric is skipped.
public final class PendingAuthorizationLoadJob
{
	private static final Logger log = LoggerFactory.getLogger(PendingAuthorizationLoadJob.class);

	private final PendingAuthorizationDatabase database;
	private final Path rootsPath;
	private final Path childrenPath;

	public PendingAuthorizationLoadJob(PendingAuthorizationDatabase database, Path rootsPath,
		Path childrenPath)
	{
		this.database = Objects.requireNonNull(database, "database");
		this.rootsPath = Objects.requireNonNull(rootsPath, "rootsPath");
		this.childrenPath = Objects.requireNonNull(childrenPath, "childrenPath");
	}

	public void run() throws IOException, SQLException
	{
		log.info("STARTING PAUDBLOD");
		log.info("*-------------------------------------*");
		log.info("TODAYS DATE            :" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd")));
		log.info(" ");
		try (InputStream roots = Files.newInputStream(rootsPath);
			InputStream children = Files.newInputStream(childrenPath))
		{
			while (true)
			{
				byte[] root = roots.readNBytes(100);
				if (root.length < 100)
					break;
				log.info(" *******************************");
				log.info(" *******************************");
				log.info(database.insertRoot(root) ? "ROOT INSERT SUCCESS    " : "ROOT SEGMENT ALREADY IN DB");
			}
			while (true)
			{
				byte[] record = children.readNBytes(6 + 200);
				if (record.length < 6 + 200)
					break;
				if (!PendingAuthorizationDatabase.isPacked(record, 0, 6))
					continue;
				log.info("***************************");
				log.info("***************************");
				Boolean inserted = database.insertChild(PendingAuthorizationDatabase.account(record),
					Arrays.copyOfRange(record, 6, record.length));
				if (inserted == null)
					continue;
				log.info("GU CALL TO ROOT SEG SUCCESS");
				log.info(inserted ? "CHILD SEGMENT INSERTED SUCCESS" : "CHILD SEGMENT ALREADY IN DB");
			}
			log.info("CLOSING THE FILE");
		}
	}
}

// Runs the load as a Spring Batch job (one tasklet step), launched by name
// (--spring.batch.job.name=pendingAuthorizationLoadJob), reading
// --carddemo.batch.paudblod.roots and --carddemo.batch.paudblod.children; or once
// at startup with --carddemo.batch.paudblod.enabled=true, after the jobs that run
// before it.
@Configuration
class PendingAuthorizationLoadJobConfiguration
{
	@Bean
	PendingAuthorizationLoadJob pendingAuthorizationLoadProgram(PendingAuthorizationDatabase database, Environment environment)
	{
		return new PendingAuthorizationLoadJob(database,
			Path.of(environment.getProperty("carddemo.batch.paudblod.roots", "pautdb-roots.dat")),
			Path.of(environment.getProperty("carddemo.batch.paudblod.children", "pautdb-children.dat")));
	}

	@Bean
	Job pendingAuthorizationLoadJob(JobRepository jobs, PlatformTransactionManager transactions,
		PendingAuthorizationLoadJob pendingAuthorizationLoadProgram)
	{
		Step step = new StepBuilder("pendingAuthorizationLoadStep", jobs)
			.tasklet((contribution, context) ->
			{
				pendingAuthorizationLoadProgram.run();
				return RepeatStatus.FINISHED;
			}, transactions)
			.build();
		return new JobBuilder("pendingAuthorizationLoadJob", jobs).start(step).build();
	}

	@Bean
	@Order(6200)
	ApplicationRunner pendingAuthorizationLoadStartupRun(Environment environment, JobLauncher launcher,
		@Qualifier("pendingAuthorizationLoadJob") Job pendingAuthorizationLoadJob)
	{
		return (ApplicationArguments arguments) ->
		{
			if (environment.getProperty("carddemo.batch.paudblod.enabled", Boolean.class, false))
				launcher.run(pendingAuthorizationLoadJob, new JobParametersBuilder()
					.addLong("startedAt", System.currentTimeMillis()).toJobParameters());
		};
	}
}

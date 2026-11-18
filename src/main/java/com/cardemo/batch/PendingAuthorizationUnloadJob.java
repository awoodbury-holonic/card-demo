package com.cardemo.batch;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

// The unload: each root of the pending-authorization database, in key order,
// to the roots file, and each of its children, in key order, to the children
// file (the root key as S9(11) COMP-3 then the child). As in the source, a root is written only when
// its key is numeric, and the end of each root's children is displayed.
public final class PendingAuthorizationUnloadJob
{
	private static final Logger log = LoggerFactory.getLogger(PendingAuthorizationUnloadJob.class);

	private final PendingAuthorizationDatabase database;
	private final Path rootsPath;
	private final Path childrenPath;

	public PendingAuthorizationUnloadJob(PendingAuthorizationDatabase database, Path rootsPath,
		Path childrenPath)
	{
		this.database = Objects.requireNonNull(database, "database");
		this.rootsPath = Objects.requireNonNull(rootsPath, "rootsPath");
		this.childrenPath = Objects.requireNonNull(childrenPath, "childrenPath");
	}

	public void run() throws IOException, SQLException
	{
		log.info("STARTING PROGRAM PAUDBUNL::");
		log.info("*-------------------------------------*");
		log.info("TODAYS DATE            :" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd")));
		log.info(" ");
		try (OutputStream roots = Files.newOutputStream(rootsPath);
			OutputStream children = Files.newOutputStream(childrenPath))
		{
			for (byte[] root : database.roots())
			{
				if (!PendingAuthorizationDatabase.isPacked(root, 0, 6))
					continue;
				roots.write(root);
				long account = PendingAuthorizationDatabase.account(root);
				for (byte[] child : database.children(account))
				{
					if (true)
						children.write(PendingAuthorizationDatabase.accountKey(account));
					children.write(child);
				}
				log.info("CHILD SEG FLAG GE : Y");
			}
			log.info("CLOSING THE FILE");
		}
	}
}

// Runs the unload as a Spring Batch job (one tasklet step), launched by name
// (--spring.batch.job.name=pendingAuthorizationUnloadJob), writing
// --carddemo.batch.paudbunl.roots and --carddemo.batch.paudbunl.children; or once
// at startup with --carddemo.batch.paudbunl.enabled=true, after the jobs that run
// before it.
@Configuration
class PendingAuthorizationUnloadJobConfiguration
{
	@Bean
	PendingAuthorizationUnloadJob pendingAuthorizationUnloadProgram(PendingAuthorizationDatabase database, Environment environment)
	{
		return new PendingAuthorizationUnloadJob(database,
			Path.of(environment.getProperty("carddemo.batch.paudbunl.roots", "pautdb-roots.dat")),
			Path.of(environment.getProperty("carddemo.batch.paudbunl.children", "pautdb-children.dat")));
	}

	@Bean
	Job pendingAuthorizationUnloadJob(JobRepository jobs, PlatformTransactionManager transactions,
		PendingAuthorizationUnloadJob pendingAuthorizationUnloadProgram)
	{
		Step step = new StepBuilder("pendingAuthorizationUnloadStep", jobs)
			.tasklet((contribution, context) ->
			{
				pendingAuthorizationUnloadProgram.run();
				return RepeatStatus.FINISHED;
			}, transactions)
			.build();
		return new JobBuilder("pendingAuthorizationUnloadJob", jobs).start(step).build();
	}

	@Bean
	@Order(6000)
	ApplicationRunner pendingAuthorizationUnloadStartupRun(Environment environment, JobLauncher launcher,
		@Qualifier("pendingAuthorizationUnloadJob") Job pendingAuthorizationUnloadJob)
	{
		return (ApplicationArguments arguments) ->
		{
			if (environment.getProperty("carddemo.batch.paudbunl.enabled", Boolean.class, false))
				launcher.run(pendingAuthorizationUnloadJob, new JobParametersBuilder()
					.addLong("startedAt", System.currentTimeMillis()).toJobParameters());
		};
	}
}

package com.cardemo.batch;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Iterator;
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

// The purge of expired authorizations. For each root, in key order, each
// child whose age in days (today's YYDDD less the date its key holds as
// 99999 minus YYDDD) reaches the expiry days is deleted and taken out of the
// root's approved (response 00) or declined count and amount; then the root
// is deleted when its approved count is not positive. The parameter record
// (--carddemo.batch.cbpaup0c.parm, default the JCL's SYSIN) gives the expiry
// days, the checkpoint and checkpoint-display frequencies and the debug flag.
// Kept from the source: the root test reads the approved count twice (never
// the declined count); the checkpoint tests compare a numeric counter with
// the alphanumeric parameter, as text ("00000001" against "00001   "); the
// day difference is a 4-digit binary field. A checkpoint commits nothing
// here: each deletion is its own transaction.
public final class PendingAuthorizationPurgeJob
{
	private static final Logger log = LoggerFactory.getLogger(PendingAuthorizationPurgeJob.class);

	private final PendingAuthorizationDatabase database;
	private final String parameter;

	private long summaryRead;
	private long summaryDeleted;
	private long detailRead;
	private long detailDeleted;
	private long checkpoints;
	private long sinceCheckpoint;

	public PendingAuthorizationPurgeJob(PendingAuthorizationDatabase database, String parameter)
	{
		this.database = Objects.requireNonNull(database, "database");
		this.parameter = pad(Objects.requireNonNull(parameter, "parameter"), 16);
	}

	public void run() throws SQLException
	{
		summaryRead = summaryDeleted = detailRead = detailDeleted = checkpoints = sinceCheckpoint = 0;
		LocalDate now = LocalDate.now();
		int today = (now.getYear() % 100) * 1000 + now.getDayOfYear();
		log.info("STARTING PROGRAM CBPAUP0C::");
		log.info("*-------------------------------------*");
		log.info("CBPAUP0C PARM RECEIVED :" + parameter);
		log.info("TODAYS DATE            :" + String.format("%05d", today));
		log.info(" ");
		int expiryDays = parameter.substring(0, 2).chars().allMatch(Character::isDigit)
			? Integer.parseInt(parameter.substring(0, 2)) : 5;
		String checkpointFrequency = blankOrZero(parameter.substring(3, 8)) ? "5" : parameter.substring(3, 8);
		String displayFrequency = blankOrZero(parameter.substring(9, 14)) ? "10" : parameter.substring(9, 14);
		boolean debug = parameter.charAt(15) == 'Y';

		long currentApplication = 0;
		Iterator<byte[]> roots = database.roots().iterator();
		while (true)
		{
			if (debug)
				log.info("DEBUG: AUTH SMRY READ : " + signed(summaryRead, 8));
			if (!roots.hasNext())
				break;
			byte[] root = roots.next();
			++summaryRead;
			++sinceCheckpoint;
			long account = PendingAuthorizationDatabase.account(root);
			currentApplication = account;
			int approvedCount = PendingAuthorizationDatabase.halfword(root, 50);
			Iterator<byte[]> children = database.children(account).iterator();
			while (true)
			{
				if (debug)
					log.info("DEBUG: AUTH DTL READ : " + signed(detailRead, 8));
				if (!children.hasNext())
					break;
				byte[] child = children.next();
				++detailRead;
				int authDate = (99999 - PendingAuthorizationDatabase.packed(child, 0, 3, 0).intValue()) % 100000;
				int dayDifference = (today - authDate) % 10000;
				if (dayDifference >= expiryDays)
				{
					if (PendingAuthorizationDatabase.text(child, 62, 2).equals("00"))
						--approvedCount;
					if (debug)
						log.info("DEBUG: AUTH DTL DLET : " + signed(account, 11));
					database.deleteChild(account, child);
					++detailDeleted;
				}
			}
			if (approvedCount <= 0 && approvedCount <= 0)
			{
				if (debug)
					log.info("DEBUG: AUTH SMRY DLET : " + signed(account, 11));
				database.deleteRoot(account);
				++summaryDeleted;
			}
			if (asText(sinceCheckpoint).compareTo(pad(checkpointFrequency, 8)) > 0)
			{
				checkpoint(displayFrequency, currentApplication);
				sinceCheckpoint = 0;
			}
		}
		checkpoint(displayFrequency, currentApplication);
		log.info(" ");
		log.info("*-------------------------------------*");
		log.info("# TOTAL SUMMARY READ  :" + signed(summaryRead, 8));
		log.info("# SUMMARY REC DELETED :" + signed(summaryDeleted, 8));
		log.info("# TOTAL DETAILS READ  :" + signed(detailRead, 8));
		log.info("# DETAILS REC DELETED :" + signed(detailDeleted, 8));
		log.info("*-------------------------------------*");
		log.info(" ");
	}

	private void checkpoint(String displayFrequency, long currentApplication)
	{
		++checkpoints;
		if (asText(checkpoints).compareTo(pad(displayFrequency, 8)) >= 0)
		{
			checkpoints = 0;
			log.info("CHKP SUCCESS: AUTH COUNT - " + signed(summaryRead, 8) + ", APP ID - "
				+ String.format("%011d", currentApplication));
		}
	}

	// A PIC 9(8) counter as the text it compares as.
	private static String asText(long value)
	{
		return String.format("%08d", value % 100_000_000L);
	}

	private static boolean blankOrZero(String value)
	{
		return value.isBlank() || value.chars().allMatch((c) -> c == '0') || value.chars().allMatch((c) -> c == 0);
	}

	// DISPLAY of a signed numeric field: its digits, the sign overpunched on
	// the last one.
	static String signed(long value, int digits)
	{
		String text = String.format("%0" + digits + "d", Math.abs(value));
		text = text.substring(text.length() - digits);
		int last = text.charAt(digits - 1) - '0';
		return text.substring(0, digits - 1) + (value < 0 ? "}JKLMNOPQR" : "{ABCDEFGHI").charAt(last);
	}

	private static String pad(String value, int width)
	{
		return value.length() >= width ? value.substring(0, width) : value + " ".repeat(width - value.length());
	}
}

// Runs the purge as a Spring Batch job (one tasklet step), launched by name
// (--spring.batch.job.name=pendingAuthorizationPurgeJob) with the parameter
// record at --carddemo.batch.cbpaup0c.parm; or once at startup with
// --carddemo.batch.cbpaup0c.enabled=true, after the jobs that run before it.
@Configuration
class PendingAuthorizationPurgeJobConfiguration
{
	@Bean
	PendingAuthorizationPurgeJob pendingAuthorizationPurgeProgram(PendingAuthorizationDatabase database, Environment environment)
	{
		return new PendingAuthorizationPurgeJob(database, environment.getProperty("carddemo.batch.cbpaup0c.parm", "00,00001,00001,Y"));
	}

	@Bean
	Job pendingAuthorizationPurgeJob(JobRepository jobs, PlatformTransactionManager transactions,
		PendingAuthorizationPurgeJob pendingAuthorizationPurgeProgram)
	{
		Step step = new StepBuilder("pendingAuthorizationPurgeStep", jobs)
			.tasklet((contribution, context) ->
			{
				pendingAuthorizationPurgeProgram.run();
				return RepeatStatus.FINISHED;
			}, transactions)
			.build();
		return new JobBuilder("pendingAuthorizationPurgeJob", jobs).start(step).build();
	}

	@Bean
	@Order(6100)
	ApplicationRunner pendingAuthorizationPurgeStartupRun(Environment environment, JobLauncher launcher,
		@Qualifier("pendingAuthorizationPurgeJob") Job pendingAuthorizationPurgeJob)
	{
		return (ApplicationArguments arguments) ->
		{
			if (environment.getProperty("carddemo.batch.cbpaup0c.enabled", Boolean.class, false))
				launcher.run(pendingAuthorizationPurgeJob, new JobParametersBuilder()
					.addLong("startedAt", System.currentTimeMillis()).toJobParameters());
		};
	}
}

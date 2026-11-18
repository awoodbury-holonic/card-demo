package com.cardemo.batch;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

// The internal reader: the online report request writes a job, line by line,
// to the JOBS queue. Each complete job (up to its /*EOF line)
// is taken off the queue; a job that runs the TRANREPT
// procedure runs this job with the dates of its //STEP10R.DATEPARM DD * lines
// (start, a space, end). Other jobs are logged and dropped. Polls every
// carddemo.intrdr.poll-ms milliseconds; off with
// --carddemo.intrdr.enabled=false.
@Component
public final class TransactionDetailReportJobInternalReader implements ApplicationRunner, DisposableBean
{
	private static final Logger log = LoggerFactory.getLogger(TransactionDetailReportJobInternalReader.class);

	private final javax.sql.DataSource dataSource;
	private final JobLauncher launcher;
	private final Job job;
	private final Environment environment;
	private ScheduledExecutorService poller;

	public TransactionDetailReportJobInternalReader(javax.sql.DataSource dataSource, JobLauncher launcher,
		@Qualifier("transactionDetailReportJob") Job job, Environment environment)
	{
		this.dataSource = dataSource;
		this.launcher = launcher;
		this.job = job;
		this.environment = environment;
	}

	@Override
	public void run(ApplicationArguments arguments)
	{
		if (!environment.getProperty("carddemo.intrdr.enabled", Boolean.class, true))
			return;
		long every = environment.getProperty("carddemo.intrdr.poll-ms", Long.class, 2000L);
		poller = Executors.newSingleThreadScheduledExecutor((task) ->
		{
			Thread thread = new Thread(task, "transactionDetailReportInternalReader");
			thread.setDaemon(true);
			return thread;
		});
		poller.scheduleWithFixedDelay(this::poll, every, every, TimeUnit.MILLISECONDS);
	}

	@Override
	public void destroy()
	{
		if (poller != null)
			poller.shutdownNow();
	}

	void poll()
	{
		try
		{
			QueuedJob queued;
			while ((queued = take()) != null)
				submit(queued);
		}
		catch (Exception failure)
		{
			log.warn("internal reader: poll failed", failure);
		}
	}

	private record QueuedJob(List<String> lines, String submissionId) { }

	// Taking a tracked job and its durable claim is atomic. A crash after
	// claiming but before an execution is linked remains visible as UNKNOWN.
	// startup never guesses that another worker's execution failed.
	private QueuedJob take() throws SQLException
	{
		try (Connection connection = dataSource.getConnection();
			PreparedStatement select = connection.prepareStatement(
				"SELECT QUEUE_SEQUENCE, RECORD_IMAGE FROM CARDEMO_TD_QUEUE "
					+ "WHERE QUEUE_NAME = ? ORDER BY QUEUE_SEQUENCE"))
		{
			connection.setAutoCommit(false);
			try (PreparedStatement lock = connection.prepareStatement(
				"SELECT LOCK_ID FROM CARDEMO_REPORT_QUEUE_LOCK WHERE LOCK_ID = 1 FOR UPDATE");
				ResultSet row = lock.executeQuery())
			{
				if (!row.next()) throw new SQLException("Report queue lock is missing");
			}
			select.setString(1, "JOBS");
			List<String> lines = new ArrayList<>();
			long last = -1;
			try (ResultSet rows = select.executeQuery())
			{
				while (rows.next())
				{
					lines.add(rows.getString(2));
					if (rows.getString(2).strip().equals("/*EOF"))
					{
						last = rows.getLong(1);
						break;
					}
				}
			}
			if (last < 0)
			{
				connection.commit();
				return null;
			}
			String submission = null;
			try (PreparedStatement find = connection.prepareStatement(
				"SELECT SUBMISSION_ID FROM CARDEMO_REPORT_SUBMISSION WHERE LAST_SEQUENCE = ? AND STATE = 'QUEUED'"))
			{
				find.setLong(1, last);
				try (ResultSet row = find.executeQuery())
				{
					if (row.next()) submission = row.getString(1);
				}
			}
			if (submission != null)
				try (PreparedStatement claim = connection.prepareStatement(
					"UPDATE CARDEMO_REPORT_SUBMISSION SET STATE = 'CLAIMED' WHERE SUBMISSION_ID = ? AND STATE = 'QUEUED'"))
				{
					claim.setString(1, submission);
					if (claim.executeUpdate() != 1) throw new SQLException("Report submission claim failed");
				}
			try (PreparedStatement delete = connection.prepareStatement(
				"DELETE FROM CARDEMO_TD_QUEUE WHERE QUEUE_NAME = ? AND QUEUE_SEQUENCE <= ?"))
			{
				delete.setString(1, "JOBS");
				delete.setLong(2, last);
				delete.executeUpdate();
			}
			connection.commit();
			return new QueuedJob(List.copyOf(lines), submission);
		}
	}

	private void submit(QueuedJob queued)
	{
		List<String> lines = queued.lines();
		String name = lines.isEmpty() ? "" : lines.get(0).strip().split("\\s+")[0].replaceFirst("^//", "");
		boolean runs = lines.stream().anyMatch((line) -> line.contains("EXEC PROC=TRANREPT"));
		int at = -1;
		for (int i = 0; i < lines.size(); i++)
			if (lines.get(i).strip().equals("//STEP10R.DATEPARM DD *"))
				at = i + 1;
		if (!runs || at < 0 || at >= lines.size() || lines.get(at).length() < 21)
		{
			log.warn("internal reader: job " + name + " not run (only TRANREPT jobs are converted)");
			failedSubmission(queued.submissionId());
			return;
		}
		String dates = lines.get(at);
		log.info("internal reader: job " + name + " submitted");
		try
		{
			JobParametersBuilder parameters = new JobParametersBuilder()
				.addString("startDate", dates.substring(0, 10))
				.addString("endDate", dates.substring(11, 21));
			if (queued.submissionId() != null)
				parameters.addString("submissionId", queued.submissionId());
			else
				parameters.addLong("startedAt", System.currentTimeMillis());
			launcher.run(job, parameters.toJobParameters());
		}
		catch (Exception failure)
		{
			failedSubmission(queued.submissionId());
			log.warn("internal reader: job " + name + " could not be launched", failure);
		}
	}

	private void failedSubmission(String id)
	{
		if (id != null)
			new org.springframework.jdbc.core.JdbcTemplate(dataSource).update(
				"UPDATE CARDEMO_REPORT_SUBMISSION SET STATE = 'FAILED' WHERE SUBMISSION_ID = ? AND EXECUTION_ID IS NULL", id);
	}
}

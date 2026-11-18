package com.cardemo.report;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;
import org.slf4j.LoggerFactory;

@org.springframework.stereotype.Repository
public class TransactionReportRequestTdQueuePortJdbcAdapter implements TransactionReportRequestTdQueuePort
{
	private final javax.sql.DataSource dataSource;

	public TransactionReportRequestTdQueuePortJdbcAdapter(javax.sql.DataSource dataSource)
	{
		this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
	}

	@Override
	public WriteResult write(WriteRequest request)
	{
		WriteRequest value = Objects.requireNonNull(request, "request");
		String sql = "INSERT INTO CARDEMO_TD_QUEUE "
			+ "(QUEUE_NAME, RECORD_IMAGE, CREATED_AT) VALUES (?, ?, CURRENT_TIMESTAMP)";
		Connection connection;
		try
		{
			connection = org.springframework.jdbc.datasource.DataSourceUtils.getConnection(dataSource);
		}
		catch (org.springframework.jdbc.CannotGetJdbcConnectionException failure)
		{
			LoggerFactory.getLogger(TransactionReportRequestTdQueuePort.class).warn("td queue port: connection failed", failure);
			return new WriteResult(WriteOutcome.REJECTED, 16, 2);
		}
		boolean transactional = org.springframework.jdbc.datasource.DataSourceUtils.isConnectionTransactional(connection, dataSource);
		try
		{
			if (!transactional)
				connection.setAutoCommit(false);
			try (PreparedStatement statement = connection.prepareStatement(sql))
			{
				statement.setString(1, value.queue());
				statement.setString(2, value.recordImage());
				if (statement.executeUpdate() != 1)
				{
					if (!transactional) connection.rollback();
					return new WriteResult(WriteOutcome.REJECTED, 16, 1);
				}
				if (!transactional) connection.commit();
				return new WriteResult(WriteOutcome.NORMAL, 0, 0);
			}
			catch (SQLException | RuntimeException failure)
			{
				try { if (!transactional) connection.rollback(); }
				catch (SQLException rollbackFailure)
				{ failure.addSuppressed(rollbackFailure); }
				throw failure;
			}
		}
		catch (SQLException failure)
		{
			LoggerFactory.getLogger(TransactionReportRequestTdQueuePort.class).warn("td queue port: write failed", failure);
			return new WriteResult(WriteOutcome.REJECTED, 16, 2);
		}
		finally
		{
			org.springframework.jdbc.datasource.DataSourceUtils.releaseConnection(connection, dataSource);
		}
	}
}

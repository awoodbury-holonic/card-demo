package com.cardemo.authorization;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import org.springframework.stereotype.Repository;
import org.slf4j.LoggerFactory;

@Repository
public class AuthfrdsJdbcRepository
	implements InsertOrUpdateRepository<AuthorizationFraudService.FraudCommand>
{
	private static final String INSERT =
		"INSERT INTO AUTHFRDS (ACCT_ID,CUST_ID,CARD_NUM,AUTH_TS,AUTH_FRAUD, " +
		"FRAUD_RPT_DATE) VALUES (?,?,?,?,?, CURRENT_DATE)";
	private static final String UPDATE =
		"UPDATE AUTHFRDS SET AUTH_FRAUD = ?, " +
		"FRAUD_RPT_DATE = CURRENT_DATE WHERE CARD_NUM = ? " +
		"AND AUTH_TS = ?";

	private final javax.sql.DataSource dataSource;

	public AuthfrdsJdbcRepository(javax.sql.DataSource dataSource)
	{
		this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
	}

	@Override
	public PersistenceOutcome save(AuthorizationFraudService.FraudCommand value)
	{
		Objects.requireNonNull(value, "value");
		try (Connection connection = dataSource.getConnection())
		{
			connection.setAutoCommit(false);
			try
			{
				try (PreparedStatement statement =
					connection.prepareStatement(INSERT))
				{
					int parameter = 1;
					writeLongText(statement, parameter++, value.accountId());
					writeLongText(statement, parameter++, value.customerId());
					writeString(statement, parameter++, value.cardNum());
					writeTimestamp(statement, parameter++, value.authTimestamp());
					writeAction(statement, parameter++, value.action());
					statement.executeUpdate();
				}
				connection.commit();
				return PersistenceOutcome.INSERTED;
			}
			catch (SQLException insertFailure)
			{
				connection.rollback();
				if (!"23505".equals(insertFailure.getSQLState()))
					return PersistenceOutcome.FAILED;
				try (PreparedStatement statement =
					connection.prepareStatement(UPDATE))
				{
					writeAction(statement, 1, value.action());
					statement.setString(2, value.cardNum());
					writeTimestamp(statement, 3, value.authTimestamp());
					if (statement.executeUpdate() != 1)
					{
						connection.rollback();
						return PersistenceOutcome.FAILED;
					}
					connection.commit();
					return PersistenceOutcome.UPDATED;
				}
				catch (SQLException updateFailure)
				{
					LoggerFactory.getLogger(AuthorizationFraudService.class).warn("two state insert update command jdbc bundle: save failed", updateFailure);
					connection.rollback();
					return PersistenceOutcome.FAILED;
				}
			}
		}
		catch (SQLException failure)
		{
			LoggerFactory.getLogger(AuthorizationFraudService.class).warn("two state insert update command jdbc bundle: save failed", failure);
			return PersistenceOutcome.FAILED;
		}
	}

	private static void writeString(PreparedStatement statement,
		int parameter, String value) throws SQLException
		{ statement.setString(parameter, value); }
	private static void writeLongText(PreparedStatement statement,
		int parameter, String value) throws SQLException
		{ statement.setLong(parameter, Long.parseLong(value)); }
	private static void writeTimestamp(PreparedStatement statement,
		int parameter, String value) throws SQLException
	{
		DateTimeFormatter sourceFormat =
			DateTimeFormatter.ofPattern("yy-MM-dd HH.mm.ssSSSSSS");
		statement.setTimestamp(parameter, Timestamp.valueOf(
			LocalDateTime.parse(value, sourceFormat)));
	}
	private static void writeAction(PreparedStatement statement,
		int parameter, AuthorizationFraudService.FraudAction value)
		throws SQLException
	{
		statement.setString(parameter, switch (value)
		{
			case REPORT -> "F";
			case REMOVE -> "R";
		});
	}
}

package com.cardemo.batch.account;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

// Seed imports run before anything else at startup, so a batch job launched
// at startup (by name, or by its startup flag) sees the imported files.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class AccountImportService implements ApplicationRunner
{
	private final javax.sql.DataSource dataSource;
	private final boolean enabled;

	public AccountImportService(javax.sql.DataSource dataSource, Environment environment)
	{
		this.dataSource = Objects.requireNonNull(dataSource,
			"dataSource");
		Environment properties = Objects.requireNonNull(environment,
			"environment");
		this.enabled = properties.getProperty("carddemo.import.accountdata.enabled",
			Boolean.class, false);
	}

	@Override
	public void run(ApplicationArguments arguments) throws Exception
	{
		Objects.requireNonNull(arguments, "arguments");
		if (enabled) importFromClasspath();
	}

	public int importFromClasspath()
		throws IOException, SQLException
	{
		InputStream resource = getClass().getClassLoader()
			.getResourceAsStream("seed-data/AWS.M2.CARDDEMO.ACCTDATA.PS");
		if (resource == null)
			throw new FileNotFoundException(
				"missing packaged seed resource: " + "seed-data/AWS.M2.CARDDEMO.ACCTDATA.PS");
		return importFrom(resource);
	}

	public int importFrom(InputStream source)
		throws IOException, SQLException
	{
		Charset charset;
		try
		{
			charset = Charset.forName(
				"IBM037");
		}
		catch (IllegalArgumentException failure)
		{
			throw new IOException("unsupported host charset", failure);
		}

		try (InputStream input = Objects.requireNonNull(source,
			"source");
			Connection connection = dataSource.getConnection())
		{
			connection.setAutoCommit(false);
			try (PreparedStatement statement = connection.prepareStatement(
				"MERGE INTO ACCTDAT (ACCT_ID,ACCT_ACTIVE_STATUS,ACCT_CURR_BAL,ACCT_CREDIT_LIMIT,ACCT_CASH_CREDIT_LIMIT,ACCT_OPEN_DATE,ACCT_EXPIRAION_DATE,ACCT_REISSUE_DATE,ACCT_CURR_CYC_CREDIT,ACCT_CURR_CYC_DEBIT,ACCT_ADDR_ZIP,ACCT_GROUP_ID) "
					+ "KEY(ACCT_ID) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)"))
			{
				int imported = 0;
				while (true)
				{
					byte[] bytes = input.readNBytes(
						Account.RECORD_LENGTH);
					if (bytes.length == 0) break;
					if (bytes.length != Account.RECORD_LENGTH)
						throw new EOFException(
							"incomplete fixed-width record image");
					Account record =
						Account.decode(new String(bytes, charset));
					statement.setObject(1, record.getAcctId());
					statement.setObject(2, record.getAcctActiveStatus());
					statement.setObject(3, record.getAcctCurrBal());
					statement.setObject(4, record.getAcctCreditLimit());
					statement.setObject(5, record.getAcctCashCreditLimit());
					statement.setObject(6, record.getAcctOpenDate());
					statement.setObject(7, record.getAcctExpiraionDate());
					statement.setObject(8, record.getAcctReissueDate());
					statement.setObject(9, record.getAcctCurrCycCredit());
					statement.setObject(10, record.getAcctCurrCycDebit());
					statement.setObject(11, record.getAcctAddrZip());
					statement.setObject(12, record.getAcctGroupId());
					statement.executeUpdate();
					++imported;
				}
				connection.commit();
				return imported;
			}
			catch (IOException | SQLException |
				RuntimeException failure)
			{
				try
				{
					connection.rollback();
				}
				catch (SQLException rollbackFailure)
				{
					failure.addSuppressed(rollbackFailure);
				}
				throw failure;
			}
		}
	}
}

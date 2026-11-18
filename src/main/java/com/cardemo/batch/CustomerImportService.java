package com.cardemo.batch;

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
public final class CustomerImportService implements ApplicationRunner
{
	private final javax.sql.DataSource dataSource;
	private final boolean enabled;

	public CustomerImportService(javax.sql.DataSource dataSource, Environment environment)
	{
		this.dataSource = Objects.requireNonNull(dataSource,
			"dataSource");
		Environment properties = Objects.requireNonNull(environment,
			"environment");
		this.enabled = properties.getProperty("carddemo.import.customerdata.enabled",
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
			.getResourceAsStream("seed-data/AWS.M2.CARDDEMO.CUSTDATA.PS");
		if (resource == null)
			throw new FileNotFoundException(
				"missing packaged seed resource: " + "seed-data/AWS.M2.CARDDEMO.CUSTDATA.PS");
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
				"MERGE INTO CUSTDAT (CUST_ID,CUST_FIRST_NAME,CUST_MIDDLE_NAME,CUST_LAST_NAME,CUST_ADDR_LINE_1,CUST_ADDR_LINE_2,CUST_ADDR_LINE_3,CUST_ADDR_STATE_CD,CUST_ADDR_COUNTRY_CD,CUST_ADDR_ZIP,CUST_PHONE_NUM_1,CUST_PHONE_NUM_2,CUST_SSN,CUST_GOVT_ISSUED_ID,CUST_DOB_YYYY_MM_DD,CUST_EFT_ACCOUNT_ID,CUST_PRI_CARD_HOLDER_IND,CUST_FICO_CREDIT_SCORE) "
					+ "KEY(CUST_ID) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"))
			{
				int imported = 0;
				while (true)
				{
					byte[] bytes = input.readNBytes(
						Customer.RECORD_LENGTH);
					if (bytes.length == 0) break;
					if (bytes.length != Customer.RECORD_LENGTH)
						throw new EOFException(
							"incomplete fixed-width record image");
					Customer record =
						Customer.decode(new String(bytes, charset));
					statement.setObject(1, record.getCustId());
					statement.setObject(2, record.getCustFirstName());
					statement.setObject(3, record.getCustMiddleName());
					statement.setObject(4, record.getCustLastName());
					statement.setObject(5, record.getCustAddrLine1());
					statement.setObject(6, record.getCustAddrLine2());
					statement.setObject(7, record.getCustAddrLine3());
					statement.setObject(8, record.getCustAddrStateCd());
					statement.setObject(9, record.getCustAddrCountryCd());
					statement.setObject(10, record.getCustAddrZip());
					statement.setObject(11, record.getCustPhoneNum1());
					statement.setObject(12, record.getCustPhoneNum2());
					statement.setObject(13, record.getCustSsn());
					statement.setObject(14, record.getCustGovtIssuedId());
					statement.setObject(15, record.getCustDobYyyyMmDd());
					statement.setObject(16, record.getCustEftAccountId());
					statement.setObject(17, record.getCustPriCardHolderInd());
					statement.setObject(18, record.getCustFicoCreditScore());
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

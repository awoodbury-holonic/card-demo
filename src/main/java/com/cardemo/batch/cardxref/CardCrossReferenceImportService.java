package com.cardemo.batch.cardxref;

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
public final class CardCrossReferenceImportService implements ApplicationRunner
{
	private final javax.sql.DataSource dataSource;
	private final boolean enabled;

	public CardCrossReferenceImportService(javax.sql.DataSource dataSource, Environment environment)
	{
		this.dataSource = Objects.requireNonNull(dataSource,
			"dataSource");
		Environment properties = Objects.requireNonNull(environment,
			"environment");
		this.enabled = properties.getProperty("carddemo.import.cardxref.enabled",
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
			.getResourceAsStream("seed-data/AWS.M2.CARDDEMO.CARDXREF.PS");
		if (resource == null)
			throw new FileNotFoundException(
				"missing packaged seed resource: " + "seed-data/AWS.M2.CARDDEMO.CARDXREF.PS");
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
				"MERGE INTO CCXREF (XREF_CARD_NUM,XREF_CUST_ID,XREF_ACCT_ID) "
					+ "KEY(XREF_CARD_NUM) VALUES (?,?,?)"))
			{
				int imported = 0;
				while (true)
				{
					byte[] bytes = input.readNBytes(
						CardCrossReference.RECORD_LENGTH);
					if (bytes.length == 0) break;
					if (bytes.length != CardCrossReference.RECORD_LENGTH)
						throw new EOFException(
							"incomplete fixed-width record image");
					CardCrossReference record =
						CardCrossReference.decode(new String(bytes, charset));
					statement.setObject(1, record.getXrefCardNum());
					statement.setObject(2, record.getXrefCustId());
					statement.setObject(3, record.getXrefAcctId());
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

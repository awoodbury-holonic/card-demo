package com.cardemo.auth;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

@org.springframework.stereotype.Service("com.cardemo.auth.SecurityUserImportService")
public final class SecurityUserImportService
{
	private final javax.sql.DataSource dataSource;

	public SecurityUserImportService(javax.sql.DataSource dataSource)
	{
		this.dataSource = Objects.requireNonNull(dataSource,
			"dataSource");
	}

	public int importFrom(Path projectRoot)
		throws IOException, SQLException
	{
		Objects.requireNonNull(projectRoot, "projectRoot");
		InputStream source = getClass().getClassLoader()
			.getResourceAsStream("seed-data/AWS.M2.CARDDEMO.USRSEC.PS");
		if (source == null)
			throw new FileNotFoundException(
				"missing packaged seed resource: " + "seed-data/AWS.M2.CARDDEMO.USRSEC.PS");
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

		try (InputStream input = source;
			Connection connection = dataSource.getConnection())
		{
			connection.setAutoCommit(false);
			try (PreparedStatement statement = connection.prepareStatement(
				"MERGE INTO USRSEC (SEC_USR_ID, SEC_USR_FNAME, "
				+ "SEC_USR_LNAME, SEC_USR_PWD, SEC_USR_TYPE, "
				+ "SEC_USR_FILLER) KEY(SEC_USR_ID) VALUES (?, ?, ?, ?, ?, ?)"))
			{
				int imported = 0;
				while (true)
				{
					byte[] bytes = input.readNBytes(SecurityUser.RECORD_LENGTH);
					if (bytes.length == 0)
						break;
					if (bytes.length != SecurityUser.RECORD_LENGTH)
						throw new EOFException(
							"incomplete fixed-width record image");
					SecurityUser record = SecurityUser.decode(
						new String(bytes, charset));
					statement.setString(1, record.getSecUsrId());
					statement.setString(2, record.getSecUsrFname());
					statement.setString(3, record.getSecUsrLname());
					statement.setString(4, record.getSecUsrPwd());
					statement.setString(5, record.getSecUsrType());
					statement.setString(6, record.getSecUsrFiller());
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

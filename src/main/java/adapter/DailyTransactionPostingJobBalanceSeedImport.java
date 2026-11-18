package com.cardemo.batch;

import java.io.EOFException;
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

// The category balance file's packaged seed (the file the program opens I-O),
// loaded at startup like the other seed imports when its flag is set.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class DailyTransactionPostingJobBalanceSeedImport implements ApplicationRunner
{
	private final javax.sql.DataSource dataSource;
	private final boolean enabled;

	public DailyTransactionPostingJobBalanceSeedImport(javax.sql.DataSource dataSource,
		Environment environment)
	{
		this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
		this.enabled = environment.getProperty("carddemo.import.tcatbal.enabled",
			Boolean.class, false);
	}

	@Override
	public void run(ApplicationArguments arguments) throws Exception
	{
		if (enabled) importSeed();
	}

	public int importSeed() throws IOException, SQLException
	{
		Charset charset = Charset.forName("IBM037");
		try (InputStream input = Objects.requireNonNull(
				getClass().getClassLoader().getResourceAsStream("seed-data/AWS.M2.CARDDEMO.TCATBALF.PS"),
				"missing packaged seed resource: seed-data/AWS.M2.CARDDEMO.TCATBALF.PS");
			Connection connection = dataSource.getConnection();
			PreparedStatement statement = connection.prepareStatement(
				"MERGE INTO TRAN_CAT_BAL (TRANCAT_ACCT_ID, TRANCAT_TYPE_CD, TRANCAT_CD, TRAN_CAT_BAL) "
					+ "KEY(TRANCAT_ACCT_ID, TRANCAT_TYPE_CD, TRANCAT_CD) VALUES (?, ?, ?, ?)"))
		{
			int imported = 0;
			while (true)
			{
				byte[] bytes = input.readNBytes(TransactionCategoryBalance.RECORD_LENGTH);
				if (bytes.length == 0) break;
				if (bytes.length != TransactionCategoryBalance.RECORD_LENGTH)
					throw new EOFException("incomplete fixed-width record image");
				TransactionCategoryBalance record = TransactionCategoryBalance.decode(new String(bytes, charset));
				statement.setLong(1, record.accountId());
				statement.setString(2, record.typeCode());
				statement.setLong(3, record.categoryCode());
				statement.setBigDecimal(4, record.balance());
				statement.executeUpdate();
				++imported;
			}
			return imported;
		}
	}
}

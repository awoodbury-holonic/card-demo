package com.cardemo.batch;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Objects;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

// The transaction category file's packaged seed, loaded at startup like the
// other seed imports when its flag is set.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class TransactionDetailReportJobCategorySeedImport implements ApplicationRunner
{
	private final javax.sql.DataSource dataSource;
	private final boolean enabled;

	public TransactionDetailReportJobCategorySeedImport(javax.sql.DataSource dataSource, Environment environment)
	{
		this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
		this.enabled = environment.getProperty("carddemo.import.trancatg.enabled", Boolean.class, false);
	}

	@Override
	public void run(ApplicationArguments arguments) throws Exception
	{
		if (!enabled)
			return;
		Charset charset = Charset.forName("IBM037");
		try (InputStream input = Objects.requireNonNull(
				getClass().getClassLoader().getResourceAsStream("seed-data/AWS.M2.CARDDEMO.TRANCATG.PS"),
				"missing packaged seed resource: seed-data/AWS.M2.CARDDEMO.TRANCATG.PS");
			Connection connection = dataSource.getConnection();
			PreparedStatement statement = connection.prepareStatement(
				"MERGE INTO TRAN_CATG (TRAN_CAT_KEY, TRAN_CAT_TYPE_DESC) KEY(TRAN_CAT_KEY) VALUES (?, ?)"))
		{
			while (true)
			{
				byte[] bytes = input.readNBytes(60);
				if (bytes.length < 60) break;
				String image = new String(bytes, charset);
				statement.setString(1, image.substring(0, 6));
				statement.setString(2, image.substring(6, 56));
				statement.executeUpdate();
			}
		}
	}
}

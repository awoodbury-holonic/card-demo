package com.cardemo.batch.interest;

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

// The disclosure group file's packaged seed, loaded at startup like the
// other seed imports when its flag is set.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class InterestCalculationJobGroupSeedImport implements ApplicationRunner
{
	private final javax.sql.DataSource dataSource;
	private final boolean enabled;

	public InterestCalculationJobGroupSeedImport(javax.sql.DataSource dataSource,
		Environment environment)
	{
		this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
		this.enabled = environment.getProperty("carddemo.import.discgrp.enabled",
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
				getClass().getClassLoader().getResourceAsStream("seed-data/AWS.M2.CARDDEMO.DISCGRP.PS"),
				"missing packaged seed resource: seed-data/AWS.M2.CARDDEMO.DISCGRP.PS");
			Connection connection = dataSource.getConnection();
			PreparedStatement statement = connection.prepareStatement(
				"MERGE INTO DISCGRP (DIS_ACCT_GROUP_ID, DIS_TRAN_TYPE_CD, DIS_TRAN_CAT_CD, DIS_INT_RATE) "
					+ "KEY(DIS_ACCT_GROUP_ID, DIS_TRAN_TYPE_CD, DIS_TRAN_CAT_CD) VALUES (?, ?, ?, ?)"))
		{
			int imported = 0;
			while (true)
			{
				byte[] bytes = input.readNBytes(DisclosureGroup.RECORD_LENGTH);
				if (bytes.length == 0) break;
				if (bytes.length != DisclosureGroup.RECORD_LENGTH)
					throw new EOFException("incomplete fixed-width record image");
				DisclosureGroup record = DisclosureGroup.decode(new String(bytes, charset));
				statement.setString(1, record.groupId().strip());
				statement.setString(2, record.typeCode());
				statement.setLong(3, record.categoryCode());
				statement.setBigDecimal(4, record.interestRate());
				statement.executeUpdate();
				++imported;
			}
			return imported;
		}
	}
}

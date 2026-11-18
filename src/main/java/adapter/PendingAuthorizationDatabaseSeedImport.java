package com.cardemo.authorization;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

// The database as the corpus ships it, its HD reorganization unload
// (seed-data/AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0.dat), loaded at startup like the other seed imports when
// its flag is set. Each unload record is RDW-prefixed: the level (1 root, 2
// child) after the RDW, the segment length at offset 8, the segment name at
// 10, the segment data before the record's last byte. Deviation: a root whose
// key is not a packed decimal (the shipped unload has one, its key all
// spaces, with no children) has no relational key and is skipped.
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class PendingAuthorizationDatabaseSeedImport implements ApplicationRunner
{
	private static final Logger log = LoggerFactory.getLogger(PendingAuthorizationDatabaseSeedImport.class);

	private final PendingAuthorizationDatabase database;
	private final boolean enabled;

	public PendingAuthorizationDatabaseSeedImport(PendingAuthorizationDatabase database, Environment environment)
	{
		this.database = database;
		this.enabled = environment.getProperty("carddemo.import.pautdb.enabled", Boolean.class, false);
	}

	@Override
	public void run(ApplicationArguments arguments) throws Exception
	{
		if (!enabled)
			return;
		byte[] bytes;
		try (InputStream input = Objects.requireNonNull(
			getClass().getClassLoader().getResourceAsStream("seed-data/AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0.dat"),
			"missing packaged seed resource: seed-data/AWS.M2.CARDDEMO.IMSDATA.DBPAUTP0.dat"))
		{
			bytes = input.readAllBytes();
		}
		long account = -1;
		int roots = 0, children = 0, skipped = 0;
		for (int at = 0; at + 4 <= bytes.length;)
		{
			int length = ((bytes[at] & 0xFF) << 8) | (bytes[at + 1] & 0xFF);
			if (length < 4) break;
			byte[] record = Arrays.copyOfRange(bytes, at, at + length);
			at += length;
			if (length < 18) continue;
			int level = record[4] & 0xFF, segment = ((record[8] & 0xFF) << 8) | (record[9] & 0xFF);
			String name = PendingAuthorizationDatabase.text(record, 10, 8);
			if (segment + 1 > length) continue;
			byte[] data = Arrays.copyOfRange(record, length - segment - 1, length - 1);
			if (level == 1 && name.equals("PAUTSUM0"))
			{
				if (!PendingAuthorizationDatabase.isPacked(data, 0, 6))
				{
					account = -1;
					++skipped;
					continue;
				}
				account = PendingAuthorizationDatabase.account(data);
				database.insertRoot(data);
				++roots;
			}
			else if (level == 2 && name.equals("PAUTDTL1") && account >= 0)
			{
				database.insertChild(account, data);
				++children;
			}
		}
		log.info("PAUTSUM0 seed import: " + roots + " roots, " + children + " children, "
			+ skipped + " roots without a packed key skipped");
	}
}

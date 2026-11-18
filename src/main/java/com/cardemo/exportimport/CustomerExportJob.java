package com.cardemo.exportimport;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;

// The source's main line as one step: open the five input files and the
// export file, export customers, accounts, cross-references, transactions and
// cards in that order (each in key order), then close and display the counts.
// Every export record is a 40-byte header -- type, the run's timestamp, a
// binary sequence number, branch and region -- and the record's data in the
// export layout: display, binary and packed fields, the rest spaces.
public final class CustomerExportJob
{
	private static final Logger log = LoggerFactory.getLogger(CustomerExportJob.class);
	private static final DateTimeFormatter ORIGIN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");
	private static final DateTimeFormatter PROCESSED = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH.mm.ss.SSSSSS");

	private final javax.sql.DataSource dataSource;
	private final Path exportPath;
	private final Charset charset;
	private OutputStream output;
	private String timestamp;
	private long sequence;

	public CustomerExportJob(javax.sql.DataSource dataSource, Path exportPath,
		Charset charset)
	{
		this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
		this.exportPath = Objects.requireNonNull(exportPath, "exportPath");
		this.charset = Objects.requireNonNull(charset, "charset");
	}

	public void run() throws IOException, SQLException
	{
		log.info("CBEXPORT: Starting Customer Data Export");
		LocalDateTime now = LocalDateTime.now();
		String date = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
		String time = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
		timestamp = date + " " + time + ".00";
		sequence = 0;
		output = Files.newOutputStream(exportPath);
		log.info("CBEXPORT: Export Date: " + date);
		log.info("CBEXPORT: Export Time: " + time);
		try (Connection connection = dataSource.getConnection())
		{
			long customers = section(connection, "customer", "Customers", "C",
				"SELECT CUST_ID, CUST_FIRST_NAME, CUST_MIDDLE_NAME, CUST_LAST_NAME, CUST_ADDR_LINE_1, CUST_ADDR_LINE_2, CUST_ADDR_LINE_3, CUST_ADDR_STATE_CD, CUST_ADDR_COUNTRY_CD, CUST_ADDR_ZIP, CUST_PHONE_NUM_1, CUST_PHONE_NUM_2, CUST_SSN, CUST_GOVT_ISSUED_ID, CUST_DOB_YYYY_MM_DD, CUST_EFT_ACCOUNT_ID, CUST_PRI_CARD_HOLDER_IND, CUST_FICO_CREDIT_SCORE FROM CUSTDAT ORDER BY CUST_ID", this::customer);
			long accounts = section(connection, "account", "Accounts", "A",
				"SELECT ACCT_ID, ACCT_ACTIVE_STATUS, ACCT_CURR_BAL, ACCT_CREDIT_LIMIT, ACCT_CASH_CREDIT_LIMIT, ACCT_OPEN_DATE, ACCT_EXPIRAION_DATE, ACCT_REISSUE_DATE, ACCT_CURR_CYC_CREDIT, ACCT_CURR_CYC_DEBIT, ACCT_ADDR_ZIP, ACCT_GROUP_ID FROM ACCTDAT ORDER BY ACCT_ID", this::account);
			long xrefs = section(connection, "cross-reference", "Cross-references", "X",
				"SELECT XREF_CARD_NUM, XREF_CUST_ID, XREF_ACCT_ID FROM CCXREF ORDER BY XREF_CARD_NUM", this::crossReference);
			long transactions = section(connection, "transaction", "Transactions", "T",
				"SELECT TRAN_ID, TRAN_TYPE_CD, TRAN_CAT_CD, TRAN_SOURCE, TRAN_DESC, TRAN_AMT, TRAN_MERCHANT_ID, TRAN_MERCHANT_NAME, TRAN_MERCHANT_CITY, TRAN_MERCHANT_ZIP, TRAN_CARD_NUM, TRAN_ORIG_TS, TRAN_PROC_TS FROM TRANSACT ORDER BY TRAN_ID",
				this::transaction);
			long cards = section(connection, "card", "Cards", "D",
				"SELECT CARD_NUM, CARD_ACCT_ID, CARD_CVV_CD, CARD_EMBOSSED_NAME, CARD_EXPIRAION_DATE, CARD_ACTIVE_STATUS FROM CARDDAT ORDER BY CARD_NUM", this::card);
			output.close();
			log.info("CBEXPORT: Export completed");
			log.info("CBEXPORT: Customers Exported: " + count(customers));
			log.info("CBEXPORT: Accounts Exported: " + count(accounts));
			log.info("CBEXPORT: XRefs Exported: " + count(xrefs));
			log.info("CBEXPORT: Transactions Exported: " + count(transactions));
			log.info("CBEXPORT: Cards Exported: " + count(cards));
			log.info("CBEXPORT: Total Records Exported: " + count(sequence));
		}
	}

	private interface RecordData
	{
		byte[] data(ResultSet row) throws SQLException;
	}

	private long section(Connection connection, String what, String plural, String type, String sql,
		RecordData data) throws SQLException, IOException
	{
		log.info("CBEXPORT: Processing " + what + " records");
		long exported = 0;
		try (PreparedStatement statement = connection.prepareStatement(sql);
			ResultSet rows = statement.executeQuery())
		{
			while (rows.next())
			{
				byte[] body = data.data(rows);
				output.write(text(type));
				output.write(text(pad(timestamp, 26)));
				output.write(HostFields.binary(++sequence, 4));
				output.write(text("0001"));
				output.write(text("NORTH"));
				output.write(body);
				output.write(text(" ".repeat(460 - body.length)));
				++exported;
			}
		}
		log.info("CBEXPORT: " + plural + " exported: " + count(exported));
		return exported;
	}

	private byte[] customer(ResultSet r) throws SQLException
	{
		return HostFields.join(HostFields.binary(r.getLong(1), 4), text(pad(r.getString(2), 25)
			+ pad(r.getString(3), 25) + pad(r.getString(4), 25) + pad(r.getString(5), 50)
			+ pad(r.getString(6), 50) + pad(r.getString(7), 50) + pad(r.getString(8), 2)
			+ pad(r.getString(9), 3) + pad(r.getString(10), 10) + pad(r.getString(11), 15)
			+ pad(r.getString(12), 15) + String.format("%09d", r.getLong(13)) + pad(r.getString(14), 20)
			+ pad(r.getString(15), 10) + pad(r.getString(16), 10) + pad(r.getString(17), 1)),
			HostFields.packedUnsigned(r.getLong(18), 3));
	}

	private byte[] account(ResultSet r) throws SQLException
	{
		return HostFields.join(text(String.format("%011d", r.getLong(1)) + pad(r.getString(2), 1)),
			HostFields.packed(r.getBigDecimal(3), 12), text(HostFields.zoned(r.getBigDecimal(4), 12)),
			HostFields.packed(r.getBigDecimal(5), 12),
			text(pad(r.getString(6), 10) + pad(r.getString(7), 10) + pad(r.getString(8), 10)
				+ HostFields.zoned(r.getBigDecimal(9), 12)),
			HostFields.binary(r.getBigDecimal(10).movePointRight(2).longValueExact(), 8),
			text(pad(r.getString(11), 10) + pad(r.getString(12), 10)));
	}

	private byte[] crossReference(ResultSet r) throws SQLException
	{
		return HostFields.join(text(pad(r.getString(1), 16) + String.format("%09d", r.getLong(2))),
			HostFields.binary(r.getLong(3), 8));
	}

	private byte[] transaction(ResultSet r) throws SQLException
	{
		return HostFields.join(text(pad(r.getString(1), 16) + pad(r.getString(2), 2)
			+ String.format("%04d", r.getLong(3)) + pad(r.getString(4), 10) + pad(r.getString(5), 100)),
			HostFields.packed(r.getBigDecimal(6), 11), HostFields.binary(r.getLong(7), 4),
			text(pad(r.getString(8), 50) + pad(r.getString(9), 50) + pad(r.getString(10), 10)
				+ pad(r.getString(11), 16) + timestampText(r, 12, ORIGIN) + timestampText(r, 13, PROCESSED)));
	}

	// The table keeps timestamps as values; the export writes each in the
	// text form its writer produced (the daily file's origin time, the
	// posting's DB2-format processing time).
	private static String timestampText(ResultSet r, int column, DateTimeFormatter format) throws SQLException
	{
		LocalDateTime value = r.getObject(column, LocalDateTime.class);
		return value == null ? " ".repeat(26) : value.format(format);
	}

	private byte[] card(ResultSet r) throws SQLException
	{
		return HostFields.join(text(pad(r.getString(1), 16)), HostFields.binary(r.getLong(2), 8),
			HostFields.binary(r.getLong(3), 2),
			text(pad(r.getString(4), 50) + pad(r.getString(5), 10) + pad(r.getString(6), 1)));
	}

	private byte[] text(String value)
	{
		return value.getBytes(charset);
	}

	private static String pad(String value, int width)
	{
		String text = value == null ? "" : value;
		return text.length() >= width ? text.substring(0, width) : text + " ".repeat(width - text.length());
	}

	private static String count(long value)
	{
		return String.format("%09d", value);
	}

	// Host record fields: signed zoned decimal (sign overpunched on the last
	// digit), packed decimal (two digits a byte, the sign in the last
	// half-byte: C, D, or F when unsigned) and big-endian binary.
	static final class HostFields
	{
		private HostFields() { }

		static String zoned(BigDecimal value, int digits)
		{
			String text = value.movePointRight(2).abs().toBigInteger().toString();
			text = "0".repeat(Math.max(0, digits - text.length())) + text;
			int last = text.charAt(text.length() - 1) - '0';
			char sign = (value.signum() < 0 ? "}JKLMNOPQR" : "{ABCDEFGHI").charAt(last);
			return text.substring(0, text.length() - 1) + sign;
		}

		static byte[] packed(BigDecimal value, int digits)
		{
			return packedDigits(value.movePointRight(2).abs().toBigInteger().toString(), digits,
				value.signum() < 0 ? 0xD : 0xC);
		}

		static byte[] packedUnsigned(long value, int digits)
		{
			return packedDigits(Long.toString(Math.abs(value)), digits, 0xF);
		}

		private static byte[] packedDigits(String digits, int count, int sign)
		{
			int size = count / 2 + 1;
			String text = "0".repeat(Math.max(0, size * 2 - 1 - digits.length())) + digits;
			byte[] out = new byte[size];
			for (int i = 0; i < size; i++)
			{
				int high = text.charAt(i * 2) - '0';
				int low = i == size - 1 ? sign : text.charAt(i * 2 + 1) - '0';
				out[i] = (byte) ((high << 4) | low);
			}
			return out;
		}

		static byte[] binary(long value, int bytes)
		{
			byte[] out = new byte[bytes];
			for (int i = bytes - 1; i >= 0; i--)
			{
				out[i] = (byte) (value & 0xFF);
				value >>= 8;
			}
			return out;
		}

		static byte[] join(byte[]... parts)
		{
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			for (byte[] part : parts)
				out.writeBytes(part);
			return out.toByteArray();
		}
	}
}

// Runs the program as a Spring Batch job (one tasklet step), the counterpart
// of its JCL step: launched by name
// (--spring.batch.job.name=customerExportJob), or once at startup
// with --carddemo.batch.export.enabled=true, after the jobs that run before it.
@Configuration
class CustomerExportJobConfiguration
{
	@Bean
	CustomerExportJob customerExportProgram(javax.sql.DataSource dataSource,
		Environment environment)
	{
		return new CustomerExportJob(dataSource, Path.of(environment.getProperty(
			"carddemo.batch.export.output", "export.dat")),
			Charset.forName("IBM037"));
	}

	@Bean
	Job customerExportJob(JobRepository jobs,
		PlatformTransactionManager transactions,
		CustomerExportJob customerExportProgram)
	{
		Step step = new StepBuilder("customerExportStep", jobs)
			.tasklet((contribution, context) ->
			{
				customerExportProgram.run();
				return RepeatStatus.FINISHED;
			}, transactions)
			.build();
		return new JobBuilder("customerExportJob", jobs).start(step).build();
	}

	@Bean
	@Order(3000)
	ApplicationRunner customerExportStartupRun(Environment environment,
		JobLauncher launcher,
		@Qualifier("customerExportJob") Job customerExportJob)
	{
		return (ApplicationArguments arguments) ->
		{
			if (environment.getProperty("carddemo.batch.export.enabled", Boolean.class, false))
				launcher.run(customerExportJob, new JobParametersBuilder()
					.addLong("startedAt", System.currentTimeMillis()).toJobParameters());
		};
	}
}

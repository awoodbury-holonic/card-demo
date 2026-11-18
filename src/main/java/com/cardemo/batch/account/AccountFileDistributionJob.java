package com.cardemo.batch.account;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;

// The source's main line as one chunk-oriented step over the account file, in
// key order. Each account is displayed, field by field, then written three
// ways: a fixed record with a packed cycle debit and a reformatted reissue
// date, an array record of balance and debit pairs, and two variable-length
// records. The source then displays the whole account record.
public final class AccountFileDistributionJob
	implements ItemReader<Account>, ItemWriter<Account>,
		JobExecutionListener
{
	private static final Logger log = LoggerFactory.getLogger(AccountFileDistributionJob.class);
	private boolean endOfFile = false;
	private Account account;

	private final AccountFileDistributionAcctfileFileRepository input;
	private final AccountFileDistributionJobAdapters.RecordFile outFile;
	private final AccountFileDistributionJobAdapters.RecordFile arrayFile;
	private final AccountFileDistributionJobAdapters.RecordFile variableFile;
	private final AccountFileDistributionStatusFormatter statusFormatter;
	private boolean finished;

	public AccountFileDistributionJob(AccountFileDistributionAcctfileFileRepository input,
		AccountFileDistributionJobAdapters.RecordFile outFile, AccountFileDistributionJobAdapters.RecordFile arrayFile,
		AccountFileDistributionJobAdapters.RecordFile variableFile, AccountFileDistributionStatusFormatter statusFormatter)
	{
		this.input = Objects.requireNonNull(input, "input");
		this.outFile = Objects.requireNonNull(outFile, "outFile");
		this.arrayFile = Objects.requireNonNull(arrayFile, "arrayFile");
		this.variableFile = Objects.requireNonNull(variableFile, "variableFile");
		this.statusFormatter = Objects.requireNonNull(statusFormatter, "statusFormatter");
	}

	@Override
	public void beforeJob(JobExecution execution)
	{
		finished = false;
		log.info("START OF EXECUTION OF PROGRAM CBACT01C");
		var opened = input.openAcctfileFile();
		if (opened.outcome() != AccountFileDistributionAcctfileFileRepository.AccessOutcome.COMPLETED)
			throw fail(opened.status(), "ERROR OPENING ACCTFILE");
		String status = outFile.open();
		if (!"00".equals(status))
			throw fail(status, "ERROR OPENING OUTFILE" + status);
		status = arrayFile.open();
		if (!"00".equals(status))
			throw fail(status, "ERROR OPENING ARRAYFILE" + status);
		status = variableFile.open();
		if (!"00".equals(status))
			throw fail(status, "ERROR OPENING VBRC FILE" + status);
	}

	@Override
	public Account read()
	{
		if (finished)
			return null;
		var result = input.readNext();
		switch (result.outcome())
		{
		case RECORD:
			return result.value();
		case END_OF_DATA:
			finish();
			return null;
		default:
			throw fail(result.status(), "ERROR READING ACCOUNT FILE");
		}
	}

	// The source closes only the account file; the outputs close when the
	// program ends.
	private void finish()
	{
		var closed = input.closeAcctfileFile();
		if (closed.outcome() != AccountFileDistributionAcctfileFileRepository.AccessOutcome.COMPLETED)
			throw fail(closed.status(), "ERROR CLOSING ACCOUNT FILE");
		outFile.close();
		arrayFile.close();
		variableFile.close();
		log.info("END OF EXECUTION OF PROGRAM CBACT01C");
		finished = true;
	}

	@Override
	public void write(Chunk<? extends Account> records)
	{
		for (Account record : records)
			distribute(record);
	}

	private void distribute(Account a)
	{
		String id = String.format("%011d", a.accountId());
		log.info("ACCT-ID                 :" + id);
		log.info("ACCT-ACTIVE-STATUS      :" + a.activeStatus());
		log.info("ACCT-CURR-BAL           :" + HostFields.zoned(a.currentBalance(), 12));
		log.info("ACCT-CREDIT-LIMIT       :" + HostFields.zoned(a.creditLimit(), 12));
		log.info("ACCT-CASH-CREDIT-LIMIT  :" + HostFields.zoned(a.cashCreditLimit(), 12));
		log.info("ACCT-OPEN-DATE          :" + HostFields.date(a.openDate()));
		log.info("ACCT-EXPIRAION-DATE     :" + HostFields.date(a.expirationDate()));
		log.info("ACCT-REISSUE-DATE       :" + HostFields.date(a.reissueDate()));
		log.info("ACCT-CURR-CYC-CREDIT    :" + HostFields.zoned(a.currentCycleCredit(), 12));
		log.info("ACCT-CURR-CYC-DEBIT     :" + HostFields.zoned(a.currentCycleDebit(), 12));
		log.info("ACCT-GROUP-ID           :" + HostFields.text(a.groupId(), 10));
		log.info("-------------------------------------------------");

		// COBDATFT, input type 2 to output type 2: YYYY-MM-DD becomes YYYYMMDD
		// in a 20-byte field the program never otherwise sets.
		String reissue = HostFields.date(a.reissueDate()).replace("-", "") + "  ";
		BigDecimal debit = a.currentCycleDebit().signum() == 0 ? new BigDecimal("2525.00")
			: a.currentCycleDebit();
		write(outFile, HostFields.bytes(id + a.activeStatus() + HostFields.zoned(a.currentBalance(), 12)
			+ HostFields.zoned(a.creditLimit(), 12) + HostFields.zoned(a.cashCreditLimit(), 12)
			+ HostFields.date(a.openDate()) + HostFields.date(a.expirationDate()) + reissue
			+ HostFields.zoned(a.currentCycleCredit(), 12)), HostFields.packed(debit, 12),
			HostFields.bytes(HostFields.text(a.groupId(), 10)));

		BigDecimal[][] pairs = {
			{ a.currentBalance(), new BigDecimal("1005.00") },
			{ a.currentBalance(), new BigDecimal("1525.00") },
			{ new BigDecimal("-1025.00"), new BigDecimal("-2500.00") },
			{ BigDecimal.ZERO, BigDecimal.ZERO },
			{ BigDecimal.ZERO, BigDecimal.ZERO } };
		ByteArrayOutputStream array = new ByteArrayOutputStream();
		array.writeBytes(HostFields.bytes(id));
		for (BigDecimal[] pair : pairs)
		{
			array.writeBytes(HostFields.bytes(HostFields.zoned(pair[0], 12)));
			array.writeBytes(HostFields.packed(pair[1], 12));
		}
		array.writeBytes(HostFields.bytes("    "));
		write(arrayFile, array.toByteArray());

		String shortRecord = id + a.activeStatus();
		String longRecord = id + HostFields.zoned(a.currentBalance(), 12)
			+ HostFields.zoned(a.creditLimit(), 12) + HostFields.date(a.reissueDate()).substring(0, 4);
		log.info("VBRC-REC1:" + shortRecord);
		log.info("VBRC-REC2:" + longRecord);
		write(variableFile, HostFields.variable(HostFields.bytes(shortRecord)));
		write(variableFile, HostFields.variable(HostFields.bytes(longRecord)));

		log.info(id + a.activeStatus() + HostFields.zoned(a.currentBalance(), 12)
			+ HostFields.zoned(a.creditLimit(), 12) + HostFields.zoned(a.cashCreditLimit(), 12)
			+ HostFields.date(a.openDate()) + HostFields.date(a.expirationDate())
			+ HostFields.date(a.reissueDate()) + HostFields.zoned(a.currentCycleCredit(), 12)
			+ HostFields.zoned(a.currentCycleDebit(), 12) + HostFields.text(a.postalCode(), 10)
			+ HostFields.text(a.groupId(), 10) + " ".repeat(178));
	}

	private void write(AccountFileDistributionJobAdapters.RecordFile file, byte[]... parts)
	{
		String status = file.write(parts);
		if (!"00".equals(status) && !"10".equals(status))
		{
			log.info("ACCOUNT FILE WRITE STATUS IS:" + status);
			throw fail(status, "ACCOUNT FILE WRITE STATUS IS:" + status);
		}
	}

	private DistributionAccessException fail(String status, String message)
	{
		log.error(message);
		log.error(statusFormatter.formatStatus(status));
		log.error("ABENDING PROGRAM");
		return new DistributionAccessException(status, message);
	}

	public static final class DistributionAccessException extends RuntimeException
	{
		private final String status;

		private DistributionAccessException(String status, String message)
		{
			super(message);
			this.status = status;
		}

		public String status()
		{
			return status;
		}
	}

	// Host record fields in the source's code page: signed zoned decimal
	// (sign overpunched on the last digit), packed decimal (two digits a byte,
	// sign in the last half-byte) and variable-length records (a four-byte
	// record descriptor word, the length including itself).
	static final class HostFields
	{
		private static final Charset charset =
			Charset.forName("IBM037");

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
			int size = digits / 2 + 1;
			String text = value.movePointRight(2).abs().toBigInteger().toString();
			text = "0".repeat(Math.max(0, size * 2 - 1 - text.length())) + text;
			byte[] out = new byte[size];
			for (int i = 0; i < size; i++)
			{
				int high = text.charAt(i * 2) - '0';
				int low = i == size - 1 ? (value.signum() < 0 ? 0xD : 0xC) : text.charAt(i * 2 + 1) - '0';
				out[i] = (byte) ((high << 4) | low);
			}
			return out;
		}

		static String date(LocalDate value)
		{
			return value == null ? " ".repeat(10) : value.toString();
		}

		static String text(String value, int width)
		{
			String text = value == null ? "" : value;
			return text.length() >= width ? text.substring(0, width)
				: text + " ".repeat(width - text.length());
		}

		static byte[] bytes(String text)
		{
			return text.getBytes(charset);
		}

		static byte[] variable(byte[] record)
		{
			byte[] out = new byte[record.length + 4];
			out[0] = (byte) ((out.length >> 8) & 0xFF);
			out[1] = (byte) (out.length & 0xFF);
			System.arraycopy(record, 0, out, 4, record.length);
			return out;
		}
	}

	// The source ends a failed run with user abend 999; kept as diagnostic data.
	public static final int ABEND_CODE = 999;
}

// Runs the program as a Spring Batch job, the counterpart of its JCL step,
// launched by name (--spring.batch.job.name=accountFileDistributionJob).
@Configuration
class AccountFileDistributionJobConfiguration
{
	@Bean
	AccountFileDistributionJob accountFileDistributionProgram(javax.sql.DataSource dataSource,
		Environment environment)
	{
		Charset hostCharset =
			Charset.forName("IBM037");
		return new AccountFileDistributionJob(new AccountFileDistributionJobAdapters.Accounts(dataSource),
			new AccountFileDistributionJobAdapters.RecordFile(Path.of(environment.getProperty(
				"carddemo.batch.readacct.outfile", "outfile.dat"))),
			new AccountFileDistributionJobAdapters.RecordFile(Path.of(environment.getProperty(
				"carddemo.batch.readacct.arryfile", "arryfile.dat"))),
			new AccountFileDistributionJobAdapters.RecordFile(Path.of(environment.getProperty(
				"carddemo.batch.readacct.vbrcfile", "vbrcfile.dat"))),
			new AccountFileDistributionStatusFormatter(hostCharset));
	}

	@Bean
	Job accountFileDistributionJob(JobRepository jobs,
		PlatformTransactionManager transactions,
		AccountFileDistributionJob accountFileDistributionProgram)
	{
		Step step = new StepBuilder("accountFileDistributionStep", jobs)
			.<Account, Account>chunk(1, transactions)
			.reader(accountFileDistributionProgram)
			.writer(accountFileDistributionProgram)
			.build();
		return new JobBuilder("accountFileDistributionJob", jobs)
			.listener(accountFileDistributionProgram)
			.start(step)
			.build();
	}
}

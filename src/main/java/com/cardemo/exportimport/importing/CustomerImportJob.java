package com.cardemo.exportimport.importing;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.transaction.PlatformTransactionManager;

// The source's main line as one step: read the export file and write each
// record, by its type, to the customer, account, cross-reference, transaction
// or card file in that file's own layout (unused bytes as spaces). A record of any
// other type goes to the error file. Then display the counts.
public final class CustomerImportJob
{
	private static final Logger log = LoggerFactory.getLogger(CustomerImportJob.class);

	public record Paths(Path input, Path customers, Path accounts,
		Path crossReferences, Path transactions, Path cards,
		Path errors) { }

	private final Paths paths;
	private final Charset charset;

	public CustomerImportJob(Paths paths, Charset charset)
	{
		this.paths = Objects.requireNonNull(paths, "paths");
		this.charset = Objects.requireNonNull(charset, "charset");
	}

	public void run() throws IOException
	{
		log.info("CBIMPORT: Starting Customer Data Import");
		LocalDateTime now = LocalDateTime.now();
		String date = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
		String time = now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
		long read = 0, customers = 0, accounts = 0, xrefs = 0, transactions = 0, cards = 0, errors = 0;
		try (InputStream input = Files.newInputStream(paths.input());
			OutputStream customerOut = Files.newOutputStream(paths.customers());
			OutputStream accountOut = Files.newOutputStream(paths.accounts());
			OutputStream xrefOut = Files.newOutputStream(paths.crossReferences());
			OutputStream transactionOut = Files.newOutputStream(paths.transactions());
			OutputStream cardOut = Files.newOutputStream(paths.cards());
			OutputStream errorOut = Files.newOutputStream(paths.errors()))
		{
			log.info("CBIMPORT: Import Date: " + date);
			log.info("CBIMPORT: Import Time: " + time);
			while (true)
			{
				byte[] record = input.readNBytes(500);
				if (record.length < 500)
					break;
				++read;
				String type = text(record, 0, 1);
				switch (type)
				{
				case "C" -> { customerOut.write(customer(record)); ++customers; }
				case "A" -> { accountOut.write(account(record)); ++accounts; }
				case "X" -> { xrefOut.write(crossReference(record)); ++xrefs; }
				case "T" -> { transactionOut.write(transaction(record)); ++transactions; }
				case "D" -> { cardOut.write(card(record)); ++cards; }
				default ->
				{
					errorOut.write(bytes(pad(LocalDateTime.now().format(
						DateTimeFormatter.ofPattern("yyyyMMddHHmmssSS")) + "+0000", 26)
						+ "|" + type + "|" + String.format("%07d", binary(record, 27, 4) % 10_000_000L)
						+ "|" + pad("Unknown record type encountered", 50) + " ".repeat(45)));
					++errors;
				}
				}
			}
		}
		log.info("CBIMPORT: Import validation completed");
		log.info("CBIMPORT: No validation errors detected");
		log.info("CBIMPORT: Import completed");
		log.info("CBIMPORT: Total Records Read: " + count(read));
		log.info("CBIMPORT: Customers Imported: " + count(customers));
		log.info("CBIMPORT: Accounts Imported: " + count(accounts));
		log.info("CBIMPORT: XRefs Imported: " + count(xrefs));
		log.info("CBIMPORT: Transactions Imported: " + count(transactions));
		log.info("CBIMPORT: Cards Imported: " + count(cards));
		log.info("CBIMPORT: Errors Written: " + count(errors));
		log.info("CBIMPORT: Unknown Record Types: " + count(errors));
	}

	// The record data starts after the 40-byte header.
	private byte[] customer(byte[] r)
	{
		return bytes(String.format("%09d", binary(r, 40, 4)) + text(r, 44, 320)
			+ String.format("%03d", packedValue(r, 364, 2)) + " ".repeat(168));
	}

	private byte[] account(byte[] r)
	{
		return bytes(text(r, 40, 12) + zoned(packedValue(r, 52, 7), 12) + text(r, 59, 12)
			+ zoned(packedValue(r, 71, 7), 12) + text(r, 78, 30) + text(r, 108, 12)
			+ zoned(binary(r, 120, 8), 12) + text(r, 128, 20) + " ".repeat(178));
	}

	private byte[] crossReference(byte[] r)
	{
		return bytes(text(r, 40, 25) + String.format("%011d", binary(r, 65, 8)) + " ".repeat(14));
	}

	private byte[] transaction(byte[] r)
	{
		return bytes(text(r, 40, 132) + zoned(packedValue(r, 172, 6), 11)
			+ String.format("%09d", binary(r, 178, 4)) + text(r, 182, 178) + " ".repeat(20));
	}

	private byte[] card(byte[] r)
	{
		return bytes(text(r, 40, 16) + String.format("%011d", binary(r, 56, 8))
			+ String.format("%03d", binary(r, 64, 2)) + text(r, 66, 61) + " ".repeat(59));
	}

	private String text(byte[] r, int from, int length)
	{
		return new String(r, from, length, charset);
	}

	private byte[] bytes(String value)
	{
		return value.getBytes(charset);
	}

	// Big-endian two's complement binary.
	private static long binary(byte[] r, int from, int length)
	{
		long value = r[from] < 0 ? -1 : 0;
		for (int i = 0; i < length; i++)
			value = (value << 8) | (r[from + i] & 0xFF);
		return value;
	}

	// Packed decimal, in its smallest unit: two digits a byte, the sign in the
	// last half-byte (D negative).
	private static long packedValue(byte[] r, int from, int length)
	{
		long value = 0;
		for (int i = 0; i < length; i++)
		{
			int b = r[from + i] & 0xFF;
			value = value * 10 + (b >> 4);
			if (i < length - 1)
				value = value * 10 + (b & 0x0F);
			else if ((b & 0x0F) == 0x0D)
				value = -value;
		}
		return value;
	}

	// Signed zoned decimal of the given width, the sign overpunched on the
	// last digit.
	private static String zoned(long value, int digits)
	{
		String text = String.format("%0" + digits + "d", Math.abs(value));
		int last = text.charAt(digits - 1) - '0';
		char sign = (value < 0 ? "}JKLMNOPQR" : "{ABCDEFGHI").charAt(last);
		return text.substring(0, digits - 1) + sign;
	}

	private static String pad(String value, int width)
	{
		return value.length() >= width ? value.substring(0, width) : value + " ".repeat(width - value.length());
	}

	private static String count(long value)
	{
		return String.format("%09d", value);
	}
}

// Runs the program as a Spring Batch job (one tasklet step), launched by
// name (--spring.batch.job.name=customerImportJob) with the export
// file at --carddemo.batch.import.input.
@Configuration
class CustomerImportJobConfiguration
{
	@Bean
	CustomerImportJob customerImportProgram(Environment environment)
	{
		Function<String[], Path> path = (names) ->
			Path.of(environment.getProperty(names[0], names[1]));
		return new CustomerImportJob(new CustomerImportJob.Paths(
			path.apply(new String[] { "carddemo.batch.import.input", "export.dat" }),
			path.apply(new String[] { "carddemo.batch.import.customers", "customers.dat" }),
			path.apply(new String[] { "carddemo.batch.import.accounts", "accounts.dat" }),
			path.apply(new String[] { "carddemo.batch.import.xrefs", "xrefs.dat" }),
			path.apply(new String[] { "carddemo.batch.import.transactions", "transactions.dat" }),
			path.apply(new String[] { "carddemo.batch.import.cards", "cards.dat" }),
			path.apply(new String[] { "carddemo.batch.import.errors", "errors.dat" })),
			Charset.forName("IBM037"));
	}

	@Bean
	Job customerImportJob(JobRepository jobs,
		PlatformTransactionManager transactions,
		CustomerImportJob customerImportProgram)
	{
		Step step = new StepBuilder("customerImportStep", jobs)
			.tasklet((contribution, context) ->
			{
				customerImportProgram.run();
				return RepeatStatus.FINISHED;
			}, transactions)
			.build();
		return new JobBuilder("customerImportJob", jobs).start(step).build();
	}
}

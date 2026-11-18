package com.cardemo.statement;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

// The statement JCL's sort step and the statement program (with its file
// access subroutine) as one step. The transactions, by card number then
// transaction id, are loaded into a table by card. Then, for each card in the
// cross-reference (in card order), the card's customer and account are read
// and one statement is written in each format, plain text (80
// bytes a record) and HTML (100): name, address and basic
// details, the card's transactions and their total. Kept from the source: each
// name part and the city end at their first space, and in the HTML the name
// and each address line end at their first double space; each HTML statement
// is a whole document. Deviation: the source first walks the system's control
// blocks to display the job, step and DD names; there is no counterpart.
public final class AccountStatementJob
{
	private static final Logger log = LoggerFactory.getLogger(AccountStatementJob.class);
	private static final String DASHES = "-".repeat(80);
	// The dollar sign, by its escape: a literal one ends this rule's text block.
	private static final String DOLLAR = "\u0024";
	private static final String TRS = "<tr>";
	private static final String TRE = "</tr>";
	private static final String TDE = "</td>";
	private static final String BANNER = "<td colspan=\"3\" style=\"padding:0px 5px;background-color:#1d1d96b3;\">";
	private static final String PLAIN = "<td colspan=\"3\" style=\"padding:0px 5px;background-color:#f2f2f2;\">";
	private static final String HEADING = "<td colspan=\"3\" style=\"padding:0px 5px;background-color:#33FFD1; text-align:center;\">";

	public record Paths(Path statements, Path html) { }

	private record Transaction(String id, String description, BigDecimal amount) { }

	private final javax.sql.DataSource dataSource;
	private final Paths paths;
	private final Charset charset;
	private OutputStream text;
	private OutputStream html;

	public AccountStatementJob(javax.sql.DataSource dataSource, Paths paths, Charset charset)
	{
		this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
		this.paths = Objects.requireNonNull(paths, "paths");
		this.charset = Objects.requireNonNull(charset, "charset");
	}

	public void run() throws IOException, SQLException
	{
		try (Connection connection = dataSource.getConnection();
			OutputStream textOut = Files.newOutputStream(paths.statements());
			OutputStream htmlOut = Files.newOutputStream(paths.html()))
		{
			text = textOut;
			html = htmlOut;
			Map<String, List<Transaction>> byCard = transactions(connection);
			try (PreparedStatement select = connection.prepareStatement("SELECT XREF_CARD_NUM, XREF_CUST_ID, XREF_ACCT_ID FROM CCXREF ORDER BY XREF_CARD_NUM");
				ResultSet xref = select.executeQuery())
			{
				while (xref.next())
				{
					String card = pad(xref.getString(1), 16);
					String[] customer = row(connection, "SELECT CUST_FIRST_NAME, CUST_MIDDLE_NAME, CUST_LAST_NAME, CUST_ADDR_LINE_1, CUST_ADDR_LINE_2, CUST_ADDR_LINE_3, CUST_ADDR_STATE_CD, CUST_ADDR_COUNTRY_CD, CUST_ADDR_ZIP, CUST_FICO_CREDIT_SCORE FROM CUSTDAT WHERE CUST_ID = ?", xref.getLong(2), "CUSTFILE");
					String[] account = row(connection, "SELECT ACCT_ID, ACCT_CURR_BAL FROM ACCTDAT WHERE ACCT_ID = ?", xref.getLong(3), "ACCTFILE");
					statement(customer, account, byCard.getOrDefault(card, List.of()));
				}
			}
		}
	}

	private Map<String, List<Transaction>> transactions(Connection connection) throws SQLException
	{
		Map<String, List<Transaction>> byCard = new LinkedHashMap<>();
		try (PreparedStatement select = connection.prepareStatement("SELECT TRAN_CARD_NUM, TRAN_ID, TRAN_DESC, TRAN_AMT FROM TRANSACT ORDER BY TRAN_CARD_NUM, TRAN_ID");
			ResultSet t = select.executeQuery())
		{
			while (t.next())
				byCard.computeIfAbsent(pad(t.getString(1), 16), (card) -> new ArrayList<>())
					.add(new Transaction(pad(t.getString(2), 16), pad(t.getString(3), 100), t.getBigDecimal(4)));
		}
		return byCard;
	}

	// One keyed read; a missing record ends the run as the source's abend does
	// (file status 23).
	private static String[] row(Connection connection, String sql, long key, String file) throws SQLException
	{
		try (PreparedStatement select = connection.prepareStatement(sql))
		{
			select.setLong(1, key);
			try (ResultSet r = select.executeQuery())
			{
				if (r.next())
				{
					String[] values = new String[r.getMetaData().getColumnCount()];
					for (int i = 0; i < values.length; i++)
						values[i] = r.getString(i + 1);
					return values;
				}
			}
		}
		log.error("ERROR READING " + file);
		log.error("RETURN CODE: 23");
		log.error("ABENDING PROGRAM");
		throw new IllegalStateException("ERROR READING " + file);
	}

	// customer: first, middle and last names, three address lines, state,
	// country, zip code, FICO score; account: id, current balance.
	private void statement(String[] customer, String[] account, List<Transaction> transactions)
		throws IOException
	{
		String name = pad(word(customer[0]) + " " + word(customer[1]) + " " + word(customer[2]) + " ", 75);
		String address1 = pad(customer[3], 50);
		String address2 = pad(customer[4], 50);
		String address3 = pad(word(customer[5]) + " " + word(customer[6]) + " " + word(customer[7]) + " "
			+ word(customer[8]) + " ", 80);
		String accountId = pad(String.format("%011d", Long.parseLong(account[0].strip())), 20);
		String balance = edited(new BigDecimal(account[1].strip()), false);
		String fico = pad(String.format("%03d", Long.parseLong(customer[9].strip())), 20);

		text("*".repeat(31) + "START OF STATEMENT" + "*".repeat(31));
		html("<!DOCTYPE html>", "<html lang=\"en\">", "<head>", "<meta charset=\"utf-8\">",
			"<title>HTML Table Layout</title>", "</head>", "<body style=\"margin:0px;\">",
			"<table  align=\"center\" frame=\"box\" style=\"width:70%; font:12px Segoe UI,sans-serif;\">",
			TRS, BANNER, "<h3>Statement for Account Number: " + accountId + "</h3>", TDE, TRE, TRS,
			"<td colspan=\"3\" style=\"padding:0px 5px;background-color:#FFAF33;\">",
			"<p style=\"font-size:16px\">Bank of XYZ</p>", "<p>410 Terry Ave N</p>", "<p>Seattle WA 99999</p>",
			TDE, TRE, TRS, PLAIN,
			"<p style=\"font-size:16px\">" + upTo(name.substring(0, 50), "  ") + "  </p>",
			"<p>" + upTo(address1, "  ") + "  </p>", "<p>" + upTo(address2, "  ") + "  </p>",
			"<p>" + upTo(address3, "  ") + "  </p>",
			TDE, TRE, TRS, HEADING, "<p style=\"font-size:16px\">Basic Details</p>", TDE, TRE, TRS, PLAIN,
			"<p>Account ID         : " + accountId + "</p>", "<p>Current Balance    : " + balance + "</p>",
			"<p>FICO Score         : " + fico + "</p>",
			TDE, TRE, TRS, HEADING, "<p style=\"font-size:16px\">Transaction Summary</p>", TDE, TRE, TRS,
			cell(25, "#33FF5E", "left"), "<p style=\"font-size:16px\">Tran ID</p>", TDE,
			cell(55, "#33FF5E", "left"), "<p style=\"font-size:16px\">Tran Details</p>", TDE,
			cell(20, "#33FF5E", "right"), "<p style=\"font-size:16px\">Amount</p>", TDE, TRE);
		text(name, address1, address2, address3, DASHES, " ".repeat(33) + "Basic Details ", DASHES,
			"Account ID         :" + accountId, "Current Balance    :" + balance, "FICO Score         :" + fico,
			DASHES, " ".repeat(30) + "TRANSACTION SUMMARY ", DASHES,
			"Tran ID         " + pad("Tran Details    ", 51) + "  Tran Amount", DASHES);

		BigDecimal total = BigDecimal.ZERO.setScale(2);
		for (Transaction t : transactions)
		{
			String amount = edited(t.amount(), true);
			String description = t.description().substring(0, 49);
			text(t.id() + " " + description + DOLLAR + amount);
			html(TRS, cell(25, "#f2f2f2", "left"), "<p>" + t.id() + "</p>", TDE,
				cell(55, "#f2f2f2", "left"), "<p>" + description + "</p>", TDE,
				cell(20, "#f2f2f2", "right"), "<p>" + amount + "</p>", TDE, TRE);
			total = total.add(t.amount());
		}
		text(DASHES, "Total EXP:" + " ".repeat(56) + DOLLAR + edited(total, true),
			"*".repeat(32) + "END OF STATEMENT" + "*".repeat(32));
		html(TRS, BANNER, "<h3>End of Statement</h3>", TDE, TRE, "</table>", "</body>", "</html>");
	}

	private static String cell(int width, String colour, String align)
	{
		return "<td style=\"width:" + width + "%; padding:0px 5px; background-color:" + colour
			+ "; text-align:" + align + ";\">";
	}

	private void text(String... lines) throws IOException
	{
		for (String line : lines)
			text.write(pad(line, 80).getBytes(charset));
	}

	private void html(String... lines) throws IOException
	{
		for (String line : lines)
			html.write(pad(line, 100).getBytes(charset));
	}

	// A STRING phrase DELIMITED BY SPACE: the value up to its first space.
	private static String word(String value)
	{
		return upTo(value == null ? "" : value, " ");
	}

	private static String upTo(String value, String delimiter)
	{
		int at = value.indexOf(delimiter);
		return at < 0 ? value : value.substring(0, at);
	}

	// The edit pictures 9(9).99- (zero-filled) and Z(9).99- (zero-suppressed):
	// nine integer digits, two decimals, a trailing minus or space.
	static String edited(BigDecimal value, boolean suppress)
	{
		BigDecimal abs = value.abs().setScale(2, RoundingMode.DOWN);
		BigInteger whole = abs.toBigInteger().mod(BigInteger.TEN.pow(9));
		String digits = suppress && whole.signum() == 0 ? " ".repeat(9)
			: String.format(suppress ? "%9d" : "%09d", whole);
		String fraction = abs.toPlainString().substring(abs.toPlainString().length() - 2);
		return digits + "." + fraction + (value.signum() < 0 ? "-" : " ");
	}

	private static String pad(String value, int width)
	{
		String text = value == null ? "" : value;
		return text.length() >= width ? text.substring(0, width) : text + " ".repeat(width - text.length());
	}
}

// Runs the statements as a Spring Batch job (one tasklet step), the
// counterpart of the CREASTMT JCL, launched by name
// (--spring.batch.job.name=accountStatementJob), writing
// --carddemo.batch.creastmt.stmtfile and --carddemo.batch.creastmt.htmlfile; or once at
// startup with --carddemo.batch.creastmt.enabled=true, after the jobs that run
// before it.
@Configuration
class AccountStatementJobConfiguration
{
	@Bean
	AccountStatementJob accountStatementProgram(javax.sql.DataSource dataSource,
		Environment environment)
	{
		return new AccountStatementJob(dataSource, new AccountStatementJob.Paths(
			Path.of(environment.getProperty("carddemo.batch.creastmt.stmtfile", "statement.dat")),
			Path.of(environment.getProperty("carddemo.batch.creastmt.htmlfile", "statement-html.dat"))),
			Charset.forName("IBM037"));
	}

	@Bean
	Job accountStatementJob(JobRepository jobs,
		PlatformTransactionManager transactions,
		AccountStatementJob accountStatementProgram)
	{
		Step step = new StepBuilder("accountStatementStep", jobs)
			.tasklet((contribution, context) ->
			{
				accountStatementProgram.run();
				return RepeatStatus.FINISHED;
			}, transactions)
			.build();
		return new JobBuilder("accountStatementJob", jobs).start(step).build();
	}

	@Bean
	@Order(5000)
	ApplicationRunner accountStatementStartupRun(Environment environment,
		JobLauncher launcher,
		@Qualifier("accountStatementJob") Job accountStatementJob)
	{
		return (ApplicationArguments arguments) ->
		{
			if (environment.getProperty("carddemo.batch.creastmt.enabled", Boolean.class, false))
				launcher.run(accountStatementJob, new JobParametersBuilder()
					.addLong("startedAt", System.currentTimeMillis()).toJobParameters());
		};
	}
}

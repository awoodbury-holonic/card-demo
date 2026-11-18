package com.cardemo.authorization;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cardemo.authorization.PendingAuthorizationDatabase;

// The pending-authorization summary screen: an account's customer and
// account details, its authorization summary (or zeros when it has none) and
// one page of its authorizations (5, in key order, newest
// first), each with its date, time, type, approval (A for response 00, else
// D), match status and approved amount. A page starts at a key (the first
// page at the account's first authorization); the next page's first key is
// returned when there is one. The screen keeps earlier pages' first keys to
// page back, as the source keeps them in its commarea.
// Deviations: the account id is accepted as 1 to 11 digits and zero-padded
// (the source's unjustified 3270 field takes all 11); when the account is not
// in the cross-reference only that message is shown (the source goes on to
// read the account and customer files with the record it had and shows the
// last of the overlaid messages).
@RestController
@RequestMapping("/api/authorizations/pending")
public final class AuthorizationSummaryController
{
	public record Authorization(String key, String transactionId, String date, String time, String type,
		String approval, String matchStatus, String amount) { }

	public record Summary(String message, boolean error, String accountId, String customerId, String name,
		String address1, String address2, String phone, String accountStatus, String creditLimit,
		String cashLimit, String approvedCount, String declinedCount, String creditBalance,
		String cashBalance, String approvedAmount, String declinedAmount, List<Authorization> rows,
		String nextKey) { }

	private final javax.sql.DataSource dataSource;
	private final PendingAuthorizationDatabase database;

	public AuthorizationSummaryController(javax.sql.DataSource dataSource, PendingAuthorizationDatabase database)
	{
		this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
		this.database = Objects.requireNonNull(database, "database");
	}

	@GetMapping
	public Summary summary(@RequestParam(name = "accountId", defaultValue = "") String accountId,
		@RequestParam(name = "startKey", defaultValue = "") String startKey) throws SQLException
	{
		String typed = accountId.strip();
		if (typed.isEmpty())
			return failure("Please enter Acct Id...", "");
		if (!typed.chars().allMatch(Character::isDigit) || typed.length() > 11)
			return failure("Acct Id must be Numeric ...", "");
		String account = "0".repeat(11 - typed.length()) + typed;
		long accountNumber = Long.parseLong(account);
		try (Connection connection = dataSource.getConnection())
		{
			String[] xref = row(connection, "SELECT XREF_CUST_ID, XREF_CARD_NUM FROM CCXREF WHERE XREF_ACCT_ID = ? ORDER BY XREF_CARD_NUM LIMIT 1", accountNumber);
			if (xref == null)
				return failure("Account:" + account + " not found in XREF file. Resp:000000013 Reas:000000080", account);
			String[] acct = row(connection, "SELECT ACCT_CREDIT_LIMIT, ACCT_CASH_CREDIT_LIMIT FROM ACCTDAT WHERE ACCT_ID = ?", accountNumber);
			if (acct == null)
				return failure("Account:" + account + " not found in ACCT file. Resp:000000013 Reas:000000080", account);
			String[] cust = row(connection, "SELECT CUST_ID, CUST_FIRST_NAME, CUST_MIDDLE_NAME, CUST_LAST_NAME, CUST_ADDR_LINE_1, CUST_ADDR_LINE_2, CUST_ADDR_LINE_3, CUST_ADDR_STATE_CD, CUST_ADDR_ZIP, CUST_PHONE_NUM_1 FROM CUSTDAT WHERE CUST_ID = ?", Long.parseLong(xref[0].strip()));
			if (cust == null)
				return failure("Customer:" + String.format("%09d", Long.parseLong(xref[0].strip()))
					+ " not found in CUST file. Resp:000000013 Reas:000000080", account);
			String name = fit(word(cust[1]) + " " + initialLetter(cust[2]) + " " + word(cust[3]), 25);
			String address1 = fit(upTo(text(cust[4]), "  ") + "," + upTo(text(cust[5]), "  "), 25);
			String address2 = fit(upTo(text(cust[6]), "  ") + "," + pad(cust[7], 2) + "," + pad(cust[8], 10).substring(0, 5), 25);
			String customerId = String.format("%09d", Long.parseLong(cust[0].strip()));
			String creditLimit = edited(new BigDecimal(acct[0].strip()), 8);
			String cashLimit = edited(new BigDecimal(acct[1].strip()), 5);

			byte[] summary = null;
			List<Authorization> rows = new ArrayList<>();
			String nextKey = null;
			if (database.hasRoot(accountNumber))
			{
				summary = database.root(accountNumber);
				List<byte[]> children = database.children(accountNumber);
				int from = 0;
				if (!startKey.isBlank())
				{
					from = -1;
					for (int i = 0; i < children.size() && from < 0; i++)
						if (key(children.get(i)).equals(startKey))
							from = i;
					if (from < 0)
						from = children.size();
				}
				for (int i = from; i < children.size() && rows.size() < 5; i++)
					rows.add(authorization(children.get(i)));
				if (from + 5 < children.size())
					nextKey = key(children.get(from + 5));
			}
			String zero = "0";
			return new Summary("", false, account, customerId, name, address1, address2, pad(cust[9], 13), "",
				creditLimit, cashLimit,
				summary == null ? zero : String.format("%03d", PendingAuthorizationDatabase.halfword(summary, 50) % 1000),
				summary == null ? zero : String.format("%03d", PendingAuthorizationDatabase.halfword(summary, 52) % 1000),
				summary == null ? zero : edited(PendingAuthorizationDatabase.packed(summary, 38, 6, 2), 8),
				summary == null ? zero : edited(PendingAuthorizationDatabase.packed(summary, 44, 6, 2), 5),
				summary == null ? zero : edited(PendingAuthorizationDatabase.packed(summary, 54, 6, 2), 5),
				summary == null ? zero : edited(PendingAuthorizationDatabase.packed(summary, 60, 6, 2), 5),
				rows, nextKey);
		}
	}

	// An authorization's key as the screen passes it: its date and time parts.
	static String key(byte[] child)
	{
		return PendingAuthorizationDatabase.packed(child, 0, 3, 0).toPlainString() + "-"
			+ PendingAuthorizationDatabase.packed(child, 3, 5, 0).toPlainString();
	}

	private static Authorization authorization(byte[] child)
	{
		String date = PendingAuthorizationDatabase.text(child, 8, 6), time = PendingAuthorizationDatabase.text(child, 14, 6);
		return new Authorization(key(child), PendingAuthorizationDatabase.text(child, 158, 15),
			date.substring(2, 4) + "/" + date.substring(4, 6) + "/" + date.substring(0, 2),
			time.substring(0, 2) + ":" + time.substring(2, 4) + ":" + time.substring(4, 6),
			PendingAuthorizationDatabase.text(child, 36, 4),
			PendingAuthorizationDatabase.text(child, 62, 2).equals("00") ? "A" : "D",
			PendingAuthorizationDatabase.text(child, 173, 1),
			edited(PendingAuthorizationDatabase.packed(child, 81, 7, 2), 8));
	}

	private Summary failure(String message, String account)
	{
		return new Summary(fit(message, 78), true, account, "", "", "", "", "", "", "", "", "", "", "", "", "", "",
			List.of(), null);
	}

	private static String[] row(Connection connection, String sql, long key) throws SQLException
	{
		try (PreparedStatement select = connection.prepareStatement(sql))
		{
			select.setLong(1, key);
			try (ResultSet r = select.executeQuery())
			{
				if (!r.next())
					return null;
				String[] values = new String[r.getMetaData().getColumnCount()];
				for (int i = 0; i < values.length; i++)
					values[i] = r.getString(i + 1);
				return values;
			}
		}
	}

	// The edit picture -z...z9.99: a minus or a space, the integer digits
	// zero-suppressed (the last always shown), two decimals.
	static String edited(BigDecimal value, int integerDigits)
	{
		BigDecimal abs = value.abs().setScale(2, RoundingMode.DOWN);
		String whole = abs.toBigInteger().mod(BigInteger.TEN.pow(integerDigits)).toString();
		String fraction = abs.toPlainString().substring(abs.toPlainString().length() - 2);
		return (value.signum() < 0 ? "-" : " ") + " ".repeat(integerDigits - whole.length()) + whole + "." + fraction;
	}

	private static String text(String value)
	{
		return value == null ? "" : value;
	}

	// STRING ... DELIMITED BY SPACES: the value up to its first space.
	private static String word(String value)
	{
		return upTo(text(value), " ");
	}

	private static String initialLetter(String value)
	{
		return text(value).isEmpty() ? " " : text(value).substring(0, 1);
	}

	private static String upTo(String value, String delimiter)
	{
		int at = value.indexOf(delimiter);
		return at < 0 ? value : value.substring(0, at);
	}

	private static String pad(String value, int width)
	{
		String v = text(value);
		return v.length() >= width ? v.substring(0, width) : v + " ".repeat(width - v.length());
	}

	private static String fit(String value, int width)
	{
		return value.length() > width ? value.substring(0, width) : value;
	}
}

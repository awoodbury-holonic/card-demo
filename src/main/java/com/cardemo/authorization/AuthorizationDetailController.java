package com.cardemo.authorization;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cardemo.authorization.PendingAuthorizationDatabase;

// The pending-authorization detail screen: one authorization of an account,
// by its key; the next one in key order; and fraud marking, which the source
// links to its fraud program: the authorization is toggled between fraud
// confirmed (F) and fraud removed (R), recorded in AUTHFRDS (inserted, or
// on a duplicate key updated) with today's date, and on success the
// authorization itself is replaced with the flag and the report date
// (MM/DD/YY).
@RestController
@RequestMapping("/api/authorizations/pending/detail")
public final class AuthorizationDetailController
{



	public record Detail(String message, boolean error, String accountId, String key, String cardNumber,
		String date, String time, String response, String reason, String processingCode, String amount,
		String posEntryMode, String source, String merchantCategory, String cardExpiry, String type,
		String transactionId, String matchStatus, String fraud, String merchantName, String merchantId,
		String merchantCity, String merchantState, String merchantZip) { }

	public record FraudRequest(String accountId, String key) { }

	private static final List<String[]> REASONS = List.of(
		new String[] { "0000", "APPROVED" }, new String[] { "3100", "INVALID CARD" },
		new String[] { "4100", "INSUFFICNT FUND" }, new String[] { "4200", "CARD NOT ACTIVE" },
		new String[] { "4300", "ACCOUNT CLOSED" }, new String[] { "4400", "EXCED DAILY LMT" },
		new String[] { "5100", "CARD FRAUD" }, new String[] { "5200", "MERCHANT FRAUD" },
		new String[] { "5300", "LOST CARD" }, new String[] { "9000", "UNKNOWN" });

	private final javax.sql.DataSource dataSource;
	private final PendingAuthorizationDatabase database;

	public AuthorizationDetailController(javax.sql.DataSource dataSource, PendingAuthorizationDatabase database)
	{
		this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
		this.database = Objects.requireNonNull(database, "database");
	}

	@GetMapping
	public Detail detail(@RequestParam(name = "accountId", defaultValue = "") String accountId,
		@RequestParam(name = "key", defaultValue = "") String key,
		@RequestParam(name = "next", defaultValue = "false") boolean next) throws SQLException
	{
		long account = account(accountId);
		if (account < 0 || key.isBlank())
			return empty("", accountId, key);
		List<byte[]> children = database.children(account);
		int at = find(children, key);
		if (at < 0)
			return empty("", accountId, key);
		if (next)
		{
			if (at + 1 >= children.size())
				return view(children.get(at), accountId, "Already at the last Authorization...");
			return view(children.get(at + 1), accountId, "");
		}
		return view(children.get(at), accountId, "");
	}

	@PostMapping("/fraud")
	public Detail markFraud(@RequestBody FraudRequest request) throws SQLException
	{
		long account = account(request.accountId());
		if (account < 0 || request.key() == null)
			return empty("", request.accountId(), request.key());
		String[] key = request.key().split("-", -1);
		if (key.length != 2 || !key[0].matches("[0-9]{1,5}") || !key[1].matches("[0-9]{1,9}"))
			return empty("", request.accountId(), request.key());
		try (Connection connection = dataSource.getConnection())
		{
			connection.setAutoCommit(false);
			try
			{
				byte[] original = database.childForUpdate(connection, account, Long.parseLong(key[0]), Long.parseLong(key[1]));
				if (original == null)
				{
					connection.rollback();
					return empty("", request.accountId(), request.key());
				}
				byte[] child = original.clone();
				boolean remove = PendingAuthorizationDatabase.text(child, 174, 1).equals("F");
				String action = remove ? "R" : "F";
				LocalDate reportDate = LocalDate.now();
				put(child, 174, action);
				put(child, 175, reportDate.format(DateTimeFormatter.ofPattern("MM/dd/yy")));
				String failure = recordFraud(connection, account, child, action);
				if (failure != null)
				{
					connection.rollback();
					return view(original, request.accountId(), failure);
				}
				database.replaceChild(connection, account, child);
				connection.commit();
				return view(child, request.accountId(), remove ? "AUTH FRAUD REMOVED..." : "AUTH MARKED FRAUD...");
			}
			catch (SQLException | RuntimeException failure)
			{
				connection.rollback();
				throw failure;
			}
		}
	}

	// The fraud program: insert the authorization into AUTHFRDS, or on a
	// duplicate key update its flag and date. Returns null, or the message.
	private String recordFraud(Connection connection, long account, byte[] child, String action) throws SQLException
	{
		String date = PendingAuthorizationDatabase.text(child, 8, 6);
		String time = String.format("%09d", 999_999_999L - PendingAuthorizationDatabase.packed(child, 3, 5, 0).longValue());
		String timestamp = "20" + date.substring(0, 2) + "-" + date.substring(2, 4) + "-" + date.substring(4, 6) + " "
			+ time.substring(0, 2) + ":" + time.substring(2, 4) + ":" + time.substring(4, 6) + "." + time.substring(6, 9) + "000";
		String card = PendingAuthorizationDatabase.text(child, 20, 16);
		{
			long customer = 0;
			try (PreparedStatement select = connection.prepareStatement("SELECT XREF_CUST_ID FROM CCXREF WHERE XREF_ACCT_ID = ? ORDER BY XREF_CARD_NUM LIMIT 1"))
			{
				select.setLong(1, account);
				try (ResultSet r = select.executeQuery())
				{
					if (r.next())
						customer = r.getLong(1);
				}
			}
			try (PreparedStatement insert = connection.prepareStatement("INSERT INTO AUTHFRDS "
				+ "(CARD_NUM, AUTH_TS, AUTH_TYPE, CARD_EXPIRY_DATE, MESSAGE_TYPE, MESSAGE_SOURCE, AUTH_ID_CODE, "
				+ "AUTH_RESP_CODE, AUTH_RESP_REASON, PROCESSING_CODE, TRANSACTION_AMT, APPROVED_AMT, "
				+ "MERCHANT_CATAGORY_CODE, ACQR_COUNTRY_CODE, POS_ENTRY_MODE, MERCHANT_ID, MERCHANT_NAME, "
				+ "MERCHANT_CITY, MERCHANT_STATE, MERCHANT_ZIP, TRANSACTION_ID, MATCH_STATUS, AUTH_FRAUD, "
				+ "FRAUD_RPT_DATE, ACCT_ID, CUST_ID) VALUES (?, CAST(? AS TIMESTAMP), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
				+ "?, ?, ?, ?, ?, ?, ?, ?, CURRENT_DATE, ?, ?)"))
			{
				insert.setString(1, card);
				insert.setString(2, timestamp);
				int column = 3, at = 36;
				for (int width : new int[] { 4, 4, 6, 6, 6, 2, 4, 6 })
				{
					insert.setString(column++, PendingAuthorizationDatabase.text(child, at, width));
					at += width;
				}
				insert.setBigDecimal(column++, PendingAuthorizationDatabase.packed(child, 74, 7, 2));
				insert.setBigDecimal(column++, PendingAuthorizationDatabase.packed(child, 81, 7, 2));
				insert.setString(column++, PendingAuthorizationDatabase.text(child, 88, 4));
				insert.setString(column++, PendingAuthorizationDatabase.text(child, 92, 3));
				insert.setInt(column++, Integer.parseInt(PendingAuthorizationDatabase.text(child, 95, 2)));
				at = 97;
				for (int width : new int[] { 15, 22, 13, 2, 9, 15, 1 })
				{
					insert.setString(column++, PendingAuthorizationDatabase.text(child, at, width));
					at += width;
				}
				insert.setString(column++, action);
				insert.setLong(column++, account);
				insert.setLong(column, customer);
				insert.executeUpdate();
				return null;
			}
			catch (SQLException failure)
			{
				if (!"23505".equals(failure.getSQLState()))
					return " SYSTEM ERROR DB2: CODE:" + failure.getErrorCode() + ", STATE: " + failure.getSQLState();
			}
			try (PreparedStatement update = connection.prepareStatement("UPDATE AUTHFRDS "
				+ "SET AUTH_FRAUD = ?, FRAUD_RPT_DATE = CURRENT_DATE WHERE CARD_NUM = ? AND AUTH_TS = CAST(? AS TIMESTAMP)"))
			{
				update.setString(1, action);
				update.setString(2, card);
				update.setString(3, timestamp);
				update.executeUpdate();
				return null;
			}
			catch (SQLException failure)
			{
				return " UPDT ERROR DB2: CODE:" + failure.getErrorCode() + ", STATE: " + failure.getSQLState();
			}
		}
	}

	private Detail view(byte[] child, String accountId, String message)
	{
		String date = PendingAuthorizationDatabase.text(child, 8, 6), time = PendingAuthorizationDatabase.text(child, 14, 6);
		String reasonCode = PendingAuthorizationDatabase.text(child, 64, 4);
		String reason = REASONS.stream().filter((r) -> r[0].equals(reasonCode)).findFirst()
			.map((r) -> r[0] + "-" + r[1]).orElse("9999-ERROR");
		String fraud = PendingAuthorizationDatabase.text(child, 174, 1);
		String expiry = PendingAuthorizationDatabase.text(child, 40, 4);
		BigDecimal approved = PendingAuthorizationDatabase.packed(child, 81, 7, 2);
		return new Detail(message, false, accountId, summaryKey(child),
			PendingAuthorizationDatabase.text(child, 20, 16),
			date.substring(2, 4) + "/" + date.substring(4, 6) + "/" + date.substring(0, 2),
			time.substring(0, 2) + ":" + time.substring(2, 4) + ":" + time.substring(4, 6),
			PendingAuthorizationDatabase.text(child, 62, 2).equals("00") ? "A" : "D", fit(reason, 20),
			PendingAuthorizationDatabase.text(child, 68, 6), edited(approved),
			PendingAuthorizationDatabase.text(child, 95, 2), PendingAuthorizationDatabase.text(child, 50, 6),
			PendingAuthorizationDatabase.text(child, 88, 4), expiry.substring(0, 2) + "/" + expiry.substring(2, 4),
			PendingAuthorizationDatabase.text(child, 36, 4), PendingAuthorizationDatabase.text(child, 158, 15),
			PendingAuthorizationDatabase.text(child, 173, 1),
			fraud.equals("F") || fraud.equals("R") ? fraud + "-" + PendingAuthorizationDatabase.text(child, 175, 8) : "-",
			PendingAuthorizationDatabase.text(child, 112, 22), PendingAuthorizationDatabase.text(child, 97, 15),
			PendingAuthorizationDatabase.text(child, 134, 13), PendingAuthorizationDatabase.text(child, 147, 2),
			PendingAuthorizationDatabase.text(child, 149, 9));
	}

	private static Detail empty(String message, String accountId, String key)
	{
		return new Detail(message, true, accountId == null ? "" : accountId, key == null ? "" : key, "", "", "", "",
			"", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "");
	}

	private static int find(List<byte[]> children, String key)
	{
		for (int i = 0; i < children.size(); i++)
			if (summaryKey(children.get(i)).equals(key))
				return i;
		return -1;
	}

	private static String summaryKey(byte[] child)
	{
		return PendingAuthorizationDatabase.packed(child, 0, 3, 0).toPlainString() + "-"
			+ PendingAuthorizationDatabase.packed(child, 3, 5, 0).toPlainString();
	}

	private static long account(String accountId)
	{
		String typed = accountId == null ? "" : accountId.strip();
		if (typed.isEmpty() || typed.length() > 11 || !typed.chars().allMatch(Character::isDigit))
			return -1;
		return Long.parseLong(typed);
	}

	private static void put(byte[] image, int from, String value)
	{
		byte[] bytes = value.getBytes(Charset.forName("IBM037"));
		System.arraycopy(bytes, 0, image, from, bytes.length);
	}

	// The edit picture -zzzzzzz9.99.
	static String edited(BigDecimal value)
	{
		BigDecimal abs = value.abs().setScale(2, RoundingMode.DOWN);
		String whole = abs.toBigInteger().mod(BigInteger.TEN.pow(8)).toString();
		String fraction = abs.toPlainString().substring(abs.toPlainString().length() - 2);
		return (value.signum() < 0 ? "-" : " ") + " ".repeat(8 - whole.length()) + whole + "." + fraction;
	}

	private static String fit(String value, int width)
	{
		return value.length() > width ? value.substring(0, width) : value;
	}
}

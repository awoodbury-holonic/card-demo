package com.cardemo.authorization;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

// Request fields and replies retain their source widths. A REST call begins a
// fresh CICS task: no accumulated reply pointer or previous detail amount.
@RestController
public final class AuthorizationDecisionController
{
	private static final Logger log = LoggerFactory.getLogger(AuthorizationDecisionController.class);
	private final javax.sql.DataSource dataSource;
	public AuthorizationDecisionController(javax.sql.DataSource dataSource) { this.dataSource = dataSource; }

	private record Account(long id, long customer, boolean found, boolean customerFound,
		BigDecimal limit, BigDecimal cash, BigDecimal balance) { }
	private record Summary(BigDecimal limit, BigDecimal balance, BigDecimal cash,
		int approvedCount, int declinedCount, BigDecimal approvedAmount, BigDecimal declinedAmount) { }

	@PostMapping(value = "/api/mq/authorizations", consumes = "text/plain", produces = "text/plain")
	public String reply(@RequestBody String request) throws SQLException
	{
		String[] raw = request.split(",", -1);
		if (raw.length < 18 || request.length() > 1000 || !request.chars().allMatch(c -> c >= 32 && c <= 126))
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expected the 18 authorization fields");
		int[] widths = {6,6,16,4,4,6,6,6,13,4,3,2,15,22,13,2,9,15};
		String[] fields = new String[18];
		for (int i = 0; i < fields.length; i++) fields[i] = field(raw[i], widths[i]);
		BigDecimal amount;
		try
		{
			amount = money(numval(fields[8]), 12);
			fields[7] = numeric(raw[7], 6);
			fields[11] = numeric(raw[11], 2);
		}
		catch (NumberFormatException failure)
		{
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid numeric authorization field");
		}
		try (Connection connection = dataSource.getConnection())
		{
			connection.setAutoCommit(false);
			try
			{
				Account account = account(connection, fields[2]);
				Summary summary = account == null ? null : summary(connection, account.id());
				BigDecimal available = summary != null ? summary.limit().subtract(summary.balance())
					: account != null && account.found() ? account.limit().subtract(account.balance()) : null;
				boolean approved = available != null && amount.compareTo(money(available, 11)) <= 0;
				String reason = approved ? "0000" : account == null || !account.found() || !account.customerFound() ? "3100" : "4100";
				BigDecimal accepted = approved ? amount : BigDecimal.ZERO;
				String response = approved ? "00" : "05";
				if (account != null)
				{
					storeSummary(connection, account, summary, accepted, approved);
					storeDetail(connection, account.id(), fields, amount, accepted, response, reason);
				}
				connection.commit();
				String digits = accepted.abs().setScale(2).toPlainString();
				String edited = (accepted.signum() < 0 ? "-" : " ") + " ".repeat(13 - digits.length()) + digits;
				return fields[2] + "," + fields[17] + "," + fields[1] + "," + response + "," + reason + "," + edited + ", ";
			}
			catch (SQLException | RuntimeException failure)
			{
				connection.rollback();
				throw failure;
			}
		}
	}

	private Account account(Connection connection, String card) throws SQLException
	{
		long id, customer;
		try (PreparedStatement query = connection.prepareStatement("SELECT XREF_ACCT_ID, XREF_CUST_ID FROM CCXREF WHERE XREF_CARD_NUM = ?"))
		{
			query.setString(1, card);
			try (ResultSet row = query.executeQuery())
			{
				if (!row.next()) { log.warn("A001 CARD NOT FOUND IN XREF: {}", card); return null; }
				id = row.getLong(1); customer = row.getLong(2);
			}
		}
		boolean found = false, customerFound;
		BigDecimal limit = BigDecimal.ZERO, cash = BigDecimal.ZERO, balance = BigDecimal.ZERO;
		// Lock the account even when no summary exists yet, serializing concurrent
		// first authorizations as well as read/decide/update on an existing root.
		try (PreparedStatement query = connection.prepareStatement("SELECT ACCT_CREDIT_LIMIT, ACCT_CASH_CREDIT_LIMIT, ACCT_CURR_BAL FROM ACCTDAT WHERE ACCT_ID = ? FOR UPDATE"))
		{
			query.setLong(1, id);
			try (ResultSet row = query.executeQuery())
			{
				if (row.next()) { found = true; limit = row.getBigDecimal(1); cash = row.getBigDecimal(2); balance = row.getBigDecimal(3); }
			}
		}
		try (PreparedStatement query = connection.prepareStatement("SELECT 1 FROM CUSTDAT WHERE CUST_ID = ?"))
		{
			query.setLong(1, customer);
			try (ResultSet row = query.executeQuery()) { customerFound = row.next(); }
		}
		if (!found) log.warn("A002 ACCT NOT FOUND IN XREF: {}", id);
		if (!customerFound) log.warn("A003 CUST NOT FOUND IN XREF: {}", customer);
		return new Account(id, customer, found, customerFound, limit, cash, balance);
	}

	private Summary summary(Connection connection, long account) throws SQLException
	{
		try (PreparedStatement query = connection.prepareStatement("SELECT PA_CREDIT_LIMIT, PA_CREDIT_BALANCE, PA_CASH_BALANCE, "
			+ "PA_APPROVED_AUTH_CNT, PA_DECLINED_AUTH_CNT, PA_APPROVED_AUTH_AMT, PA_DECLINED_AUTH_AMT FROM PAUTSUM0 WHERE PA_ACCT_ID = ? FOR UPDATE"))
		{
			query.setLong(1, account);
			try (ResultSet row = query.executeQuery())
			{
				return row.next() ? new Summary(row.getBigDecimal(1), row.getBigDecimal(2), row.getBigDecimal(3),
					row.getInt(4), row.getInt(5), row.getBigDecimal(6), row.getBigDecimal(7)) : null;
			}
		}
	}

	private void storeSummary(Connection connection, Account account, Summary previous, BigDecimal accepted, boolean approved) throws SQLException
	{
		Summary s = previous == null ? new Summary(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO) : previous;
		if (previous == null)
			try (PreparedStatement insert = connection.prepareStatement("INSERT INTO PAUTSUM0 (PA_ACCT_ID, PA_CUST_ID, PA_AUTH_STATUS, PA_ACCOUNT_STATUS, PA_FILLER) VALUES (?, ?, ' ', '          ', ?)"))
			{
				insert.setLong(1, account.id()); insert.setLong(2, account.customer()); insert.setString(3, " ".repeat(34)); insert.executeUpdate();
			}
		try (PreparedStatement update = connection.prepareStatement("UPDATE PAUTSUM0 SET PA_CREDIT_LIMIT=?, PA_CASH_LIMIT=?, PA_CREDIT_BALANCE=?, PA_CASH_BALANCE=?, "
			+ "PA_APPROVED_AUTH_CNT=?, PA_DECLINED_AUTH_CNT=?, PA_APPROVED_AUTH_AMT=?, PA_DECLINED_AUTH_AMT=? WHERE PA_ACCT_ID=?"))
		{
			update.setBigDecimal(1, money(account.limit(), 11)); update.setBigDecimal(2, money(account.cash(), 11));
			update.setBigDecimal(3, money(s.balance().add(accepted), 11)); update.setBigDecimal(4, approved ? BigDecimal.ZERO : s.cash());
			update.setInt(5, (s.approvedCount() + (approved ? 1 : 0)) % 10000);
			update.setInt(6, (s.declinedCount() + (approved ? 0 : 1)) % 10000);
			update.setBigDecimal(7, money(s.approvedAmount().add(accepted), 11));
			update.setBigDecimal(8, s.declinedAmount()); // prior detail amount is zero in a new task
			update.setLong(9, account.id()); update.executeUpdate();
		}
	}

	private void storeDetail(Connection connection, long account, String[] f, BigDecimal amount, BigDecimal accepted, String response, String reason) throws SQLException
	{
		LocalDateTime now = LocalDateTime.now();
		int dateKey = 99999 - ((now.getYear() % 100) * 1000 + now.getDayOfYear());
		long timeKey = 999999999L - ((now.getHour()*10000L + now.getMinute()*100L + now.getSecond())*1000 + now.getNano()/1000000);
		String columns = "PA_ACCT_ID,PA_AUTH_DATE_9C,PA_AUTH_TIME_9C,PA_AUTH_ORIG_DATE,PA_AUTH_ORIG_TIME,PA_CARD_NUM,PA_AUTH_TYPE,PA_CARD_EXPIRY_DATE,"
			+ "PA_MESSAGE_TYPE,PA_MESSAGE_SOURCE,PA_AUTH_ID_CODE,PA_AUTH_RESP_CODE,PA_AUTH_RESP_REASON,PA_PROCESSING_CODE,PA_TRANSACTION_AMT,PA_APPROVED_AMT,"
			+ "PA_MERCHANT_CATAGORY_CODE,PA_ACQR_COUNTRY_CODE,PA_POS_ENTRY_MODE,PA_MERCHANT_ID,PA_MERCHANT_NAME,PA_MERCHANT_CITY,PA_MERCHANT_STATE,PA_MERCHANT_ZIP,"
			+ "PA_TRANSACTION_ID,PA_MATCH_STATUS,PA_AUTH_FRAUD,PA_FRAUD_RPT_DATE,PA_FILLER";
		try (PreparedStatement insert = connection.prepareStatement("INSERT INTO PAUTDTL1 (" + columns + ") VALUES (" + "?,".repeat(28) + "?)"))
		{
			insert.setLong(1,account); insert.setInt(2,dateKey); insert.setLong(3,timeKey);
			int column=4;
			for (int i=0;i<7;i++) insert.setString(column++,f[i]);
			insert.setString(column++,f[1]); insert.setString(column++,response); insert.setString(column++,reason);
			insert.setInt(column++,Integer.parseInt(f[7])); insert.setBigDecimal(column++,amount); insert.setBigDecimal(column++,accepted);
			insert.setString(column++,f[9]); insert.setString(column++,f[10]); insert.setInt(column++,Integer.parseInt(f[11]));
			for (int i=12;i<18;i++) insert.setString(column++,f[i]);
			insert.setString(column++,approvedStatus(response)); insert.setString(column++," "); insert.setString(column++,"        ");
			insert.setString(column," ".repeat(17)); insert.executeUpdate();
		}
		catch (SQLException failure)
		{
			// DL/I ISRT duplicate keys log I004 and retain the prior summary update.
			if (!"23505".equals(failure.getSQLState())) throw failure;
			log.warn("I004 IMS INSERT DETL FAILED (duplicate authorization key): {}", account);
		}
	}
	private static String approvedStatus(String response) { return response.equals("00") ? "P" : "D"; }
	private static String numeric(String value, int width)
	{
		String digits = new BigInteger(value.strip()).abs().mod(BigInteger.TEN.pow(width)).toString();
		return "0".repeat(width - digits.length()) + digits;
	}
	private static BigDecimal money(BigDecimal value, int digits)
	{
		return value.setScale(2,RoundingMode.DOWN).remainder(BigDecimal.TEN.pow(digits-2));
	}
	private static BigDecimal numval(String value)
	{
		String text = value.replace(" ", "");
		boolean negative = text.endsWith("CR") || text.endsWith("DB") || text.endsWith("-");
		if (text.endsWith("CR") || text.endsWith("DB")) text=text.substring(0,text.length()-2);
		else if (text.endsWith("-") || text.endsWith("+")) text=text.substring(0,text.length()-1);
		BigDecimal number = new BigDecimal(text);
		return negative ? number.negate() : number;
	}
	private static String field(String value, int width) { return (value + " ".repeat(width)).substring(0,width); }
}

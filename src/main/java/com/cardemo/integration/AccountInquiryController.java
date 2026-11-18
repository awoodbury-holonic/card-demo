package com.cardemo.integration;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class AccountInquiryController
{
	private final javax.sql.DataSource dataSource;
	public AccountInquiryController(javax.sql.DataSource dataSource) { this.dataSource = dataSource; }

	@PostMapping(value = "/api/mq/account", consumes = "text/plain", produces = "text/plain")
	public String reply(@RequestBody(required = false) String request) throws SQLException
	{
		String input = field(request, 1000);
		String operation = input.substring(0, 4), key = input.substring(4, 15);
		String invalid = "INVALID REQUEST PARAMETERS ACCT ID : " + key;
		if (!operation.equals("INQA") || !key.chars().allMatch(c -> c >= '0' && c <= '9') || Long.parseLong(key) == 0)
			return field(invalid + "FUNCTION : " + operation, 1000);
		try (Connection connection = dataSource.getConnection();
			PreparedStatement query = connection.prepareStatement("SELECT ACCT_ACTIVE_STATUS, ACCT_CURR_BAL, ACCT_CREDIT_LIMIT, ACCT_CASH_CREDIT_LIMIT, ACCT_OPEN_DATE, ACCT_EXPIRAION_DATE, ACCT_REISSUE_DATE, ACCT_CURR_CYC_CREDIT, ACCT_CURR_CYC_DEBIT, ACCT_GROUP_ID FROM ACCTDAT WHERE ACCT_ID = ?"))
		{
			query.setLong(1, Long.parseLong(key));
			try (ResultSet row = query.executeQuery())
			{
				if (!row.next()) return field(invalid, 1000);
				return field("ACCOUNT ID : " + key + "ACCOUNT STATUS : " + field(row.getString(1), 1)
					+ "BALANCE : " + zoned(row.getBigDecimal(2)) + "CREDIT LIMIT : " + zoned(row.getBigDecimal(3))
					+ "CASH LIMIT : " + zoned(row.getBigDecimal(4)) + "OPEN DATE : " + field(row.getString(5), 10)
					+ "EXPR DATE : " + field(row.getString(6), 10) + "REIS DATE : " + field(row.getString(7), 10)
					+ "CREDIT BAL : " + zoned(row.getBigDecimal(8)) + "DEBIT BAL : " + zoned(row.getBigDecimal(9))
					+ "GROUP ID : " + field(row.getString(10), 10), 1000);
			}
		}
	}

	private static String zoned(BigDecimal value)
	{
		String digits = value.movePointRight(2).toBigInteger().abs().mod(BigInteger.TEN.pow(12)).toString();
		digits = "0".repeat(12 - digits.length()) + digits;
		String signs = value.signum() < 0 ? "}JKLMNOPQR" : "{ABCDEFGHI";
		return digits.substring(0, 11) + signs.charAt(digits.charAt(11) - '0');
	}

	private static String field(String value, int width)
	{
		String text = value == null ? "" : value;
		return (text + " ".repeat(width)).substring(0, width);
	}
}

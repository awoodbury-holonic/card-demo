package com.cardemo.account;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;

public record AccountValue(
	long accountId,
	String status,
	BigDecimal currentBalance,
	BigDecimal creditLimit,
	BigDecimal cashCreditLimit,
	LocalDate openedOn,
	LocalDate expiresOn,
	LocalDate reissueOn,
	BigDecimal currentCycleCredits,
	BigDecimal currentCycleDebits,
	String groupId
)
{
	// The source compares its records field by field, numbers by value:
	// 7000.00 and 7000 are the same amount, though BigDecimal.equals and so
	// the record's own equals see different scales.
	public boolean sameAs(AccountValue other)
	{
		if (other == null) return false;
		if (!same(accountId, other.accountId())) return false;
		if (!same(status, other.status())) return false;
		if (!same(currentBalance, other.currentBalance())) return false;
		if (!same(creditLimit, other.creditLimit())) return false;
		if (!same(cashCreditLimit, other.cashCreditLimit())) return false;
		if (!same(openedOn, other.openedOn())) return false;
		if (!same(expiresOn, other.expiresOn())) return false;
		if (!same(reissueOn, other.reissueOn())) return false;
		if (!same(currentCycleCredits, other.currentCycleCredits())) return false;
		if (!same(currentCycleDebits, other.currentCycleDebits())) return false;
		if (!same(groupId, other.groupId())) return false;
		return true;
	}

	private static boolean same(BigDecimal left, BigDecimal right)
	{
		return left == null ? right == null
			: right != null && left.compareTo(right) == 0;
	}

	private static boolean same(Object left, Object right)
	{
		return Objects.equals(left, right);
	}
}

@Component
final class AccountValueJdbcRepository
{
	private static final String SELECT_FOR_UPDATE =
		"SELECT ACCT_ID,ACCT_ACTIVE_STATUS,ACCT_CURR_BAL,ACCT_CREDIT_LIMIT,ACCT_CASH_CREDIT_LIMIT,ACCT_OPEN_DATE,ACCT_EXPIRAION_DATE,ACCT_REISSUE_DATE,ACCT_CURR_CYC_CREDIT,ACCT_CURR_CYC_DEBIT,ACCT_GROUP_ID FROM ACCTDAT " +
		"WHERE ACCT_ID = ? FOR UPDATE";
	private static final String UPDATE =
		"UPDATE ACCTDAT SET ACCT_ID = ?,ACCT_ACTIVE_STATUS = ?,ACCT_CURR_BAL = ?,ACCT_CREDIT_LIMIT = ?,ACCT_CASH_CREDIT_LIMIT = ?,ACCT_OPEN_DATE = ?,ACCT_EXPIRAION_DATE = ?,ACCT_REISSUE_DATE = ?,ACCT_CURR_CYC_CREDIT = ?,ACCT_CURR_CYC_DEBIT = ?,ACCT_GROUP_ID = ? " +
		"WHERE ACCT_ID = ?";

	Optional<AccountValue> findForUpdate(Connection connection,
		Object key) throws SQLException
	{
		try (PreparedStatement statement =
				connection.prepareStatement(SELECT_FOR_UPDATE))
		{
			statement.setObject(1, key);
			try (ResultSet rows = statement.executeQuery())
			{
				if (!rows.next()) return Optional.empty();
				AccountValue value = new AccountValue(
					readLong(rows, "ACCT_ID", 11),
					readText(rows, "ACCT_ACTIVE_STATUS", 1),
					readDecimal(rows, "ACCT_CURR_BAL", 12),
					readDecimal(rows, "ACCT_CREDIT_LIMIT", 12),
					readDecimal(rows, "ACCT_CASH_CREDIT_LIMIT", 12),
					readDate(rows, "ACCT_OPEN_DATE", 10),
					readDate(rows, "ACCT_EXPIRAION_DATE", 10),
					readDate(rows, "ACCT_REISSUE_DATE", 10),
					readDecimal(rows, "ACCT_CURR_CYC_CREDIT", 12),
					readDecimal(rows, "ACCT_CURR_CYC_DEBIT", 12),
					readText(rows, "ACCT_GROUP_ID", 10)
);
				if (rows.next())
					throw new SQLException("non-unique aggregate key");
				return Optional.of(value);
			}
		}
	}

	boolean update(Connection connection, AccountValue value)
		throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement(UPDATE))
		{
			int parameter = 1;
			writeLong(statement, parameter++, value.accountId());
			writeText(statement, parameter++, value.status());
			writeDecimal(statement, parameter++, value.currentBalance());
			writeDecimal(statement, parameter++, value.creditLimit());
			writeDecimal(statement, parameter++, value.cashCreditLimit());
			writeDate(statement, parameter++, value.openedOn());
			writeDate(statement, parameter++, value.expiresOn());
			writeDate(statement, parameter++, value.reissueOn());
			writeDecimal(statement, parameter++, value.currentCycleCredits());
			writeDecimal(statement, parameter++, value.currentCycleDebits());
			writeText(statement, parameter++, value.groupId());
			statement.setObject(parameter, value.accountId());
			return statement.executeUpdate() == 1;
		}
	}

	private static String readText(ResultSet rows, String column, int sourceWidth)
		throws SQLException
	{
		String value = rows.getString(column);
		return value == null ? null : value.stripTrailing();
	}

	// Numeric SQL storage omits leading zeros from source digit identifiers.
	// Restore their declared width before comparing or returning a snapshot.

	private static long readLong(ResultSet rows, String column, int sourceWidth)
		throws SQLException
	{
		return rows.getLong(column);
	}

	private static BigDecimal readDecimal(ResultSet rows,
		String column, int sourceWidth) throws SQLException
	{
		BigDecimal value = rows.getBigDecimal(column);
		return value == null ? null : value.stripTrailingZeros();
	}

	private static LocalDate readDate(ResultSet rows,
		String column, int sourceWidth) throws SQLException
	{
		String value = rows.getString(column);
		return value == null || value.isBlank() ? null
			: LocalDate.parse(value.trim());
	}

	private static void writeText(PreparedStatement statement, int index,
		String value) throws SQLException
	{
		statement.setString(index, value);
	}

	private static void writeLong(PreparedStatement statement, int index,
		long value) throws SQLException
	{
		statement.setLong(index, value);
	}

	private static void writeDecimal(PreparedStatement statement,
		int index, BigDecimal value) throws SQLException
	{
		statement.setBigDecimal(index, value);
	}

	private static void writeDate(PreparedStatement statement, int index,
		LocalDate value) throws SQLException
	{
		statement.setString(index, value == null ? null : value.toString());
	}
}

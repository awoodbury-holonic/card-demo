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

public record CustomerValue(
	long customerId,
	String firstName,
	String middleName,
	String lastName,
	String addressLine1,
	String addressLine2,
	String city,
	String state,
	String country,
	String postalCode,
	String phoneNumber1,
	String phoneNumber2,
	String socialSecurityNumber,
	String governmentIssuedId,
	LocalDate dateOfBirth,
	String eftAccountId,
	boolean primaryCardHolder,
	Integer ficoCreditScore
)
{
	// The source compares its records field by field, numbers by value:
	// 7000.00 and 7000 are the same amount, though BigDecimal.equals and so
	// the record's own equals see different scales.
	public boolean sameAs(CustomerValue other)
	{
		if (other == null) return false;
		if (!same(customerId, other.customerId())) return false;
		if (!same(firstName, other.firstName())) return false;
		if (!same(middleName, other.middleName())) return false;
		if (!same(lastName, other.lastName())) return false;
		if (!same(addressLine1, other.addressLine1())) return false;
		if (!same(addressLine2, other.addressLine2())) return false;
		if (!same(city, other.city())) return false;
		if (!same(state, other.state())) return false;
		if (!same(country, other.country())) return false;
		if (!same(postalCode, other.postalCode())) return false;
		if (!same(phoneNumber1, other.phoneNumber1())) return false;
		if (!same(phoneNumber2, other.phoneNumber2())) return false;
		if (!same(socialSecurityNumber, other.socialSecurityNumber())) return false;
		if (!same(governmentIssuedId, other.governmentIssuedId())) return false;
		if (!same(dateOfBirth, other.dateOfBirth())) return false;
		if (!same(eftAccountId, other.eftAccountId())) return false;
		if (!same(primaryCardHolder, other.primaryCardHolder())) return false;
		if (!same(ficoCreditScore, other.ficoCreditScore())) return false;
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
final class CustomerValueJdbcRepository
{
	private static final String SELECT_FOR_UPDATE =
		"SELECT CUST_ID,CUST_FIRST_NAME,CUST_MIDDLE_NAME,CUST_LAST_NAME,CUST_ADDR_LINE_1,CUST_ADDR_LINE_2,CUST_ADDR_LINE_3,CUST_ADDR_STATE_CD,CUST_ADDR_COUNTRY_CD,CUST_ADDR_ZIP,CUST_PHONE_NUM_1,CUST_PHONE_NUM_2,CUST_SSN,CUST_GOVT_ISSUED_ID,CUST_DOB_YYYY_MM_DD,CUST_EFT_ACCOUNT_ID,CUST_PRI_CARD_HOLDER_IND,CUST_FICO_CREDIT_SCORE FROM CUSTDAT " +
		"WHERE CUST_ID = ? FOR UPDATE";
	private static final String UPDATE =
		"UPDATE CUSTDAT SET CUST_ID = ?,CUST_FIRST_NAME = ?,CUST_MIDDLE_NAME = ?,CUST_LAST_NAME = ?,CUST_ADDR_LINE_1 = ?,CUST_ADDR_LINE_2 = ?,CUST_ADDR_LINE_3 = ?,CUST_ADDR_STATE_CD = ?,CUST_ADDR_COUNTRY_CD = ?,CUST_ADDR_ZIP = ?,CUST_PHONE_NUM_1 = ?,CUST_PHONE_NUM_2 = ?,CUST_SSN = ?,CUST_GOVT_ISSUED_ID = ?,CUST_DOB_YYYY_MM_DD = ?,CUST_EFT_ACCOUNT_ID = ?,CUST_PRI_CARD_HOLDER_IND = ?,CUST_FICO_CREDIT_SCORE = ? " +
		"WHERE CUST_ID = ?";

	Optional<CustomerValue> findForUpdate(Connection connection,
		Object key) throws SQLException
	{
		try (PreparedStatement statement =
				connection.prepareStatement(SELECT_FOR_UPDATE))
		{
			statement.setObject(1, key);
			try (ResultSet rows = statement.executeQuery())
			{
				if (!rows.next()) return Optional.empty();
				CustomerValue value = new CustomerValue(
					readLong(rows, "CUST_ID", 9),
					readText(rows, "CUST_FIRST_NAME", 25),
					readText(rows, "CUST_MIDDLE_NAME", 25),
					readText(rows, "CUST_LAST_NAME", 25),
					readText(rows, "CUST_ADDR_LINE_1", 50),
					readText(rows, "CUST_ADDR_LINE_2", 50),
					readText(rows, "CUST_ADDR_LINE_3", 50),
					readText(rows, "CUST_ADDR_STATE_CD", 2),
					readText(rows, "CUST_ADDR_COUNTRY_CD", 3),
					readText(rows, "CUST_ADDR_ZIP", 10),
					readText(rows, "CUST_PHONE_NUM_1", 15),
					readText(rows, "CUST_PHONE_NUM_2", 15),
					readDigits(rows, "CUST_SSN", 9),
					readText(rows, "CUST_GOVT_ISSUED_ID", 20),
					readDate(rows, "CUST_DOB_YYYY_MM_DD", 10),
					readText(rows, "CUST_EFT_ACCOUNT_ID", 10),
					readBoolean(rows, "CUST_PRI_CARD_HOLDER_IND", 1),
					readInteger(rows, "CUST_FICO_CREDIT_SCORE", 3)
);
				if (rows.next())
					throw new SQLException("non-unique aggregate key");
				return Optional.of(value);
			}
		}
	}

	boolean update(Connection connection, CustomerValue value)
		throws SQLException
	{
		try (PreparedStatement statement = connection.prepareStatement(UPDATE))
		{
			int parameter = 1;
			writeLong(statement, parameter++, value.customerId());
			writeText(statement, parameter++, value.firstName());
			writeText(statement, parameter++, value.middleName());
			writeText(statement, parameter++, value.lastName());
			writeText(statement, parameter++, value.addressLine1());
			writeText(statement, parameter++, value.addressLine2());
			writeText(statement, parameter++, value.city());
			writeText(statement, parameter++, value.state());
			writeText(statement, parameter++, value.country());
			writeText(statement, parameter++, value.postalCode());
			writeText(statement, parameter++, value.phoneNumber1());
			writeText(statement, parameter++, value.phoneNumber2());
			writeText(statement, parameter++, value.socialSecurityNumber());
			writeText(statement, parameter++, value.governmentIssuedId());
			writeDate(statement, parameter++, value.dateOfBirth());
			writeText(statement, parameter++, value.eftAccountId());
			writeBoolean(statement, parameter++, value.primaryCardHolder());
			writeInteger(statement, parameter++, value.ficoCreditScore());
			statement.setObject(parameter, value.customerId());
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
	private static String readDigits(ResultSet rows, String column, int sourceWidth)
		throws SQLException
	{
		String value = rows.getString(column);
		if (value == null) return null;
		if (value.isEmpty() || value.length() > sourceWidth ||
			!value.chars().allMatch(digit -> digit >= '0' && digit <= '9'))
			throw new SQLException("Invalid digit identifier in " + column);
		return "0".repeat(sourceWidth - value.length()) + value;
	}

	private static long readLong(ResultSet rows, String column, int sourceWidth)
		throws SQLException
	{
		return rows.getLong(column);
	}

	private static LocalDate readDate(ResultSet rows,
		String column, int sourceWidth) throws SQLException
	{
		String value = rows.getString(column);
		return value == null || value.isBlank() ? null
			: LocalDate.parse(value.trim());
	}

	private static boolean readBoolean(ResultSet rows, String column, int sourceWidth)
		throws SQLException
	{
		return "Y".equalsIgnoreCase(rows.getString(column));
	}

	private static Integer readInteger(ResultSet rows, String column, int sourceWidth)
		throws SQLException
	{
		Object value = rows.getObject(column);
		return value == null ? null : ((Number)value).intValue();
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

	private static void writeDate(PreparedStatement statement, int index,
		LocalDate value) throws SQLException
	{
		statement.setString(index, value == null ? null : value.toString());
	}

	private static void writeBoolean(PreparedStatement statement,
		int index, boolean value) throws SQLException
	{
		statement.setString(index, value ? "Y" : "N");
	}

	private static void writeInteger(PreparedStatement statement,
		int index, Integer value) throws SQLException
	{
		if (value == null) statement.setNull(index, Types.INTEGER);
		else statement.setInt(index, value);
	}
}

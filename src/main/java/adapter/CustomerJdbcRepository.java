package com.cardemo.batch;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@org.springframework.stereotype.Repository
public class CustomerJdbcRepository
{
	private static final String UPSERT =
		"MERGE INTO CUSTDAT (CUST_ID,CUST_FIRST_NAME,CUST_MIDDLE_NAME,CUST_LAST_NAME,CUST_ADDR_LINE_1,CUST_ADDR_LINE_2,CUST_ADDR_LINE_3,CUST_ADDR_STATE_CD,CUST_ADDR_COUNTRY_CD,CUST_ADDR_ZIP,CUST_PHONE_NUM_1,CUST_PHONE_NUM_2,CUST_SSN,CUST_GOVT_ISSUED_ID,CUST_DOB_YYYY_MM_DD,CUST_EFT_ACCOUNT_ID,CUST_PRI_CARD_HOLDER_IND,CUST_FICO_CREDIT_SCORE) " +
		"KEY(CUST_ID) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
	private static final String SELECT_AFTER =
		"SELECT CUST_ID,CUST_FIRST_NAME,CUST_MIDDLE_NAME,CUST_LAST_NAME,CUST_ADDR_LINE_1,CUST_ADDR_LINE_2,CUST_ADDR_LINE_3,CUST_ADDR_STATE_CD,CUST_ADDR_COUNTRY_CD,CUST_ADDR_ZIP,CUST_PHONE_NUM_1,CUST_PHONE_NUM_2,CUST_SSN,CUST_GOVT_ISSUED_ID,CUST_DOB_YYYY_MM_DD,CUST_EFT_ACCOUNT_ID,CUST_PRI_CARD_HOLDER_IND,CUST_FICO_CREDIT_SCORE FROM CUSTDAT " +
		"WHERE CUST_ID > ? ORDER BY CUST_ID LIMIT ?";

	private final javax.sql.DataSource dataSource;

	public CustomerJdbcRepository(javax.sql.DataSource dataSource)
	{
		this.dataSource = Objects.requireNonNull(dataSource,
			"dataSource");
	}

	public void save(Customer value)
	{
		Objects.requireNonNull(value, "value");
		try (Connection connection = dataSource.getConnection())
		{
			connection.setAutoCommit(false);
			try (PreparedStatement statement =
				connection.prepareStatement(UPSERT))
			{
				int parameter = 1;
				writeLong(statement, parameter++, value.custId());
				writeString(statement, parameter++, value.custFirstName());
				writeString(statement, parameter++, value.custMiddleName());
				writeString(statement, parameter++, value.custLastName());
				writeString(statement, parameter++, value.custAddrLine1());
				writeString(statement, parameter++, value.custAddrLine2());
				writeString(statement, parameter++, value.custAddrLine3());
				writeString(statement, parameter++, value.custAddrStateCd());
				writeString(statement, parameter++, value.custAddrCountryCd());
				writeString(statement, parameter++, value.custAddrZip());
				writeString(statement, parameter++, value.custPhoneNum1());
				writeString(statement, parameter++, value.custPhoneNum2());
				writeLong(statement, parameter++, value.custSsn());
				writeString(statement, parameter++, value.custGovtIssuedId());
				writeDate(statement, parameter++, value.custDobYyyyMmDd());
				writeString(statement, parameter++, value.custEftAccountId());
				writeString(statement, parameter++, value.custPriCardHolderInd());
				writeLong(statement, parameter++, value.custFicoCreditScore());
				statement.executeUpdate();
				connection.commit();
			}
			catch (SQLException | RuntimeException failure)
			{
				connection.rollback();
				throw failure;
			}
		}
		catch (SQLException failure)
		{
			throw new IllegalStateException("record persistence failed", failure);
		}
	}

	public List<Customer> findAfter(
		long startExclusive, int limit)
	{
		if (limit < 1) throw new IllegalArgumentException("limit must be positive");
		ArrayList<Customer> values =
			new ArrayList<>();
		try (Connection connection = dataSource.getConnection();
			PreparedStatement statement =
				connection.prepareStatement(SELECT_AFTER))
		{
			writeLong(statement, 1, startExclusive);
			statement.setInt(2, limit);
			try (ResultSet rows = statement.executeQuery())
			{
				while (rows.next())
					values.add(new Customer(
						readLong(rows, "CUST_ID"),
						readString(rows, "CUST_FIRST_NAME"),
						readString(rows, "CUST_MIDDLE_NAME"),
						readString(rows, "CUST_LAST_NAME"),
						readString(rows, "CUST_ADDR_LINE_1"),
						readString(rows, "CUST_ADDR_LINE_2"),
						readString(rows, "CUST_ADDR_LINE_3"),
						readString(rows, "CUST_ADDR_STATE_CD"),
						readString(rows, "CUST_ADDR_COUNTRY_CD"),
						readString(rows, "CUST_ADDR_ZIP"),
						readString(rows, "CUST_PHONE_NUM_1"),
						readString(rows, "CUST_PHONE_NUM_2"),
						readLong(rows, "CUST_SSN"),
						readString(rows, "CUST_GOVT_ISSUED_ID"),
						readDate(rows, "CUST_DOB_YYYY_MM_DD"),
						readString(rows, "CUST_EFT_ACCOUNT_ID"),
						readString(rows, "CUST_PRI_CARD_HOLDER_IND"),
						readLong(rows, "CUST_FICO_CREDIT_SCORE")
					));
			}
			return List.copyOf(values);
		}
		catch (SQLException failure)
		{
			throw new IllegalStateException("keyset read failed", failure);
		}
	}

	private static String readString(ResultSet rows, String column)
		throws SQLException { return rows.getString(column); }
	private static long readLong(ResultSet rows, String column)
		throws SQLException { return rows.getLong(column); }
	private static LocalDate readDate(ResultSet rows,
		String column) throws SQLException
	{
		String value = rows.getString(column);
		return value == null || value.isBlank() ? null
			: LocalDate.parse(value.trim());
	}
	private static void writeString(PreparedStatement statement,
		int parameter, String value) throws SQLException
		{ statement.setString(parameter, value); }
	private static void writeLong(PreparedStatement statement,
		int parameter, long value) throws SQLException
		{ statement.setLong(parameter, value); }
	private static void writeDate(PreparedStatement statement,
		int parameter, LocalDate value) throws SQLException
		{ statement.setString(parameter, value == null ? null : value.toString()); }
}

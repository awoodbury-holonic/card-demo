package com.cardemo.authorization;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

// The pending-authorization database (IMS DBPAUTP0, decision D-IMS) as two
// tables: PAUTSUM0, the summary (root) segments by account, and
// PAUTDTL1, the authorization (child) segments by account and
// authorization key, in IMS sequence order (the key's date and time parts,
// both positive packed decimals, so numeric order is byte order). Segments
// pass in and out as their host images (100 and
// 200 bytes), so the programs that copy them to files copy
// the same bytes; the tables keep each field, FILLER included.
@Component
public final class PendingAuthorizationDatabase
{
	private static final Charset HOST = Charset.forName("IBM037");
	private static final String ROOT_COLUMNS = "PA_ACCT_ID, PA_CUST_ID, PA_AUTH_STATUS, PA_ACCOUNT_STATUS, "
		+ "PA_CREDIT_LIMIT, PA_CASH_LIMIT, PA_CREDIT_BALANCE, PA_CASH_BALANCE, PA_APPROVED_AUTH_CNT, "
		+ "PA_DECLINED_AUTH_CNT, PA_APPROVED_AUTH_AMT, PA_DECLINED_AUTH_AMT, PA_FILLER";
	private static final String CHILD_COLUMNS = "PA_ACCT_ID, PA_AUTH_DATE_9C, PA_AUTH_TIME_9C, PA_AUTH_ORIG_DATE, "
		+ "PA_AUTH_ORIG_TIME, PA_CARD_NUM, PA_AUTH_TYPE, PA_CARD_EXPIRY_DATE, PA_MESSAGE_TYPE, PA_MESSAGE_SOURCE, "
		+ "PA_AUTH_ID_CODE, PA_AUTH_RESP_CODE, PA_AUTH_RESP_REASON, PA_PROCESSING_CODE, PA_TRANSACTION_AMT, "
		+ "PA_APPROVED_AMT, PA_MERCHANT_CATAGORY_CODE, PA_ACQR_COUNTRY_CODE, PA_POS_ENTRY_MODE, PA_MERCHANT_ID, "
		+ "PA_MERCHANT_NAME, PA_MERCHANT_CITY, PA_MERCHANT_STATE, PA_MERCHANT_ZIP, PA_TRANSACTION_ID, "
		+ "PA_MATCH_STATUS, PA_AUTH_FRAUD, PA_FRAUD_RPT_DATE, PA_FILLER";

	private final javax.sql.DataSource dataSource;

	public PendingAuthorizationDatabase(javax.sql.DataSource dataSource)
	{
		this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
	}

	// The root images in account order.
	public List<byte[]> roots() throws SQLException
	{
		try (Connection connection = dataSource.getConnection();
			PreparedStatement select = connection.prepareStatement(
				"SELECT " + ROOT_COLUMNS + " FROM PAUTSUM0 ORDER BY PA_ACCT_ID");
			ResultSet r = select.executeQuery())
		{
			List<byte[]> roots = new ArrayList<>();
			while (r.next())
				roots.add(rootImage(r));
			return roots;
		}
	}

	// One root's image, or null.
	public byte[] root(long account) throws SQLException
	{
		try (Connection connection = dataSource.getConnection();
			PreparedStatement select = connection.prepareStatement(
				"SELECT " + ROOT_COLUMNS + " FROM PAUTSUM0 WHERE PA_ACCT_ID = ?"))
		{
			select.setLong(1, account);
			try (ResultSet r = select.executeQuery())
			{
				return r.next() ? rootImage(r) : null;
			}
		}
	}

	// Replaces a child (DL/I REPL): the row with its key takes the image's fields.
	public void replaceChild(long account, byte[] image) throws SQLException
	{
		try (Connection connection = dataSource.getConnection())
		{
			replaceChild(connection, account, image);
		}
	}

	public void replaceChild(Connection connection, long account, byte[] image) throws SQLException
	{
		String[] fields = CHILD_COLUMNS.split(", ");
		String assignments = Arrays.stream(fields).skip(3).map(field -> field + " = ?")
			.collect(Collectors.joining(", "));
		try (PreparedStatement update = connection.prepareStatement("UPDATE PAUTDTL1 SET " + assignments
				+ " WHERE PA_ACCT_ID = ? AND PA_AUTH_DATE_9C = ? AND PA_AUTH_TIME_9C = ?"))
		{
			int at = 8, column = 1;
			for (int width : new int[] { 6, 6, 16, 4, 4, 6, 6, 6, 2, 4 })
			{
				update.setString(column++, text(image, at, width));
				at += width;
			}
			update.setInt(column++, Integer.parseInt(text(image, 68, 6)));
			update.setBigDecimal(column++, packed(image, 74, 7, 2));
			update.setBigDecimal(column++, packed(image, 81, 7, 2));
			update.setString(column++, text(image, 88, 4));
			update.setString(column++, text(image, 92, 3));
			update.setInt(column++, Integer.parseInt(text(image, 95, 2)));
			at = 97;
			for (int width : new int[] { 15, 22, 13, 2, 9, 15, 1, 1, 8, 17 })
			{
				update.setString(column++, text(image, at, width));
				at += width;
			}
			update.setLong(column++, account);
			update.setLong(column++, packed(image, 0, 3, 0).longValueExact());
			update.setLong(column, packed(image, 3, 5, 0).longValueExact());
			if (update.executeUpdate() != 1)
				throw new SQLException("authorization detail disappeared before replacement");
		}
	}

	// A keyed child locked for a caller-owned update transaction.
	public byte[] childForUpdate(Connection connection, long account, long date, long time) throws SQLException
	{
		try (PreparedStatement select = connection.prepareStatement("SELECT " + CHILD_COLUMNS
			+ " FROM PAUTDTL1 WHERE PA_ACCT_ID = ? AND PA_AUTH_DATE_9C = ? AND PA_AUTH_TIME_9C = ? FOR UPDATE"))
		{
			select.setLong(1, account);
			select.setLong(2, date);
			select.setLong(3, time);
			try (ResultSet r = select.executeQuery())
			{
				return r.next() ? childImage(r) : null;
			}
		}
	}

	// One root's child images in key order.
	public List<byte[]> children(long account) throws SQLException
	{
		try (Connection connection = dataSource.getConnection();
			PreparedStatement select = connection.prepareStatement("SELECT " + CHILD_COLUMNS
				+ " FROM PAUTDTL1 WHERE PA_ACCT_ID = ? ORDER BY PA_AUTH_DATE_9C, PA_AUTH_TIME_9C"))
		{
			select.setLong(1, account);
			try (ResultSet r = select.executeQuery())
			{
				List<byte[]> children = new ArrayList<>();
				while (r.next())
					children.add(childImage(r));
				return children;
			}
		}
	}

	public boolean hasRoot(long account) throws SQLException
	{
		try (Connection connection = dataSource.getConnection();
			PreparedStatement select = connection.prepareStatement(
				"SELECT 1 FROM PAUTSUM0 WHERE PA_ACCT_ID = ?"))
		{
			select.setLong(1, account);
			try (ResultSet r = select.executeQuery())
			{
				return r.next();
			}
		}
	}

	// Inserts a root; false when one with its key is present (status II).
	public boolean insertRoot(byte[] image) throws SQLException
	{
		if (hasRoot(account(image)))
			return false;
		try (Connection connection = dataSource.getConnection();
			PreparedStatement insert = connection.prepareStatement("INSERT INTO PAUTSUM0 ("
				+ ROOT_COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"))
		{
			insert.setLong(1, account(image));
			insert.setLong(2, Long.parseLong(text(image, 6, 9)));
			insert.setString(3, text(image, 15, 1));
			insert.setString(4, text(image, 16, 10));
			insert.setBigDecimal(5, packed(image, 26, 6, 2));
			insert.setBigDecimal(6, packed(image, 32, 6, 2));
			insert.setBigDecimal(7, packed(image, 38, 6, 2));
			insert.setBigDecimal(8, packed(image, 44, 6, 2));
			insert.setInt(9, halfword(image, 50));
			insert.setInt(10, halfword(image, 52));
			insert.setBigDecimal(11, packed(image, 54, 6, 2));
			insert.setBigDecimal(12, packed(image, 60, 6, 2));
			insert.setString(13, text(image, 66, 34));
			insert.executeUpdate();
			return true;
		}
	}

	// Inserts a child under its root: null when the root is missing, false
	// when a child with its key is present (status II).
	public Boolean insertChild(long account, byte[] image) throws SQLException
	{
		if (!hasRoot(account))
			return null;
		long date = packed(image, 0, 3, 0).longValueExact(), time = packed(image, 3, 5, 0).longValueExact();
		try (Connection connection = dataSource.getConnection())
		{
			try (PreparedStatement select = connection.prepareStatement("SELECT 1 FROM PAUTDTL1 "
				+ "WHERE PA_ACCT_ID = ? AND PA_AUTH_DATE_9C = ? AND PA_AUTH_TIME_9C = ?"))
			{
				select.setLong(1, account);
				select.setLong(2, date);
				select.setLong(3, time);
				try (ResultSet r = select.executeQuery())
				{
					if (r.next())
						return false;
				}
			}
			try (PreparedStatement insert = connection.prepareStatement("INSERT INTO PAUTDTL1 ("
				+ CHILD_COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"))
			{
				insert.setLong(1, account);
				insert.setLong(2, date);
				insert.setLong(3, time);
				int at = 8, column = 4;
				for (int width : new int[] { 6, 6, 16, 4, 4, 6, 6, 6, 2, 4 })
				{
					insert.setString(column++, text(image, at, width));
					at += width;
				}
				insert.setInt(column++, Integer.parseInt(text(image, 68, 6)));
				insert.setBigDecimal(column++, packed(image, 74, 7, 2));
				insert.setBigDecimal(column++, packed(image, 81, 7, 2));
				insert.setString(column++, text(image, 88, 4));
				insert.setString(column++, text(image, 92, 3));
				insert.setInt(column++, Integer.parseInt(text(image, 95, 2)));
				at = 97;
				for (int width : new int[] { 15, 22, 13, 2, 9, 15, 1, 1, 8, 17 })
				{
					insert.setString(column++, text(image, at, width));
					at += width;
				}
				insert.executeUpdate();
				return true;
			}
		}
	}

	public void deleteChild(long account, byte[] image) throws SQLException
	{
		try (Connection connection = dataSource.getConnection();
			PreparedStatement delete = connection.prepareStatement("DELETE FROM PAUTDTL1 "
				+ "WHERE PA_ACCT_ID = ? AND PA_AUTH_DATE_9C = ? AND PA_AUTH_TIME_9C = ?"))
		{
			delete.setLong(1, account);
			delete.setLong(2, packed(image, 0, 3, 0).longValueExact());
			delete.setLong(3, packed(image, 3, 5, 0).longValueExact());
			delete.executeUpdate();
		}
	}

	// Deletes a root and, as IMS does, its children.
	public void deleteRoot(long account) throws SQLException
	{
		try (Connection connection = dataSource.getConnection())
		{
			for (String table : new String[] { "PAUTDTL1", "PAUTSUM0" })
				try (PreparedStatement delete = connection.prepareStatement(
					"DELETE FROM " + table + " WHERE PA_ACCT_ID = ?"))
				{
					delete.setLong(1, account);
					delete.executeUpdate();
				}
		}
	}

	public static long account(byte[] rootImage)
	{
		return packed(rootImage, 0, 6, 0).longValueExact();
	}

	// PIC S9(11) COMP-3 as six bytes, sign C.
	public static byte[] accountKey(long account)
	{
		byte[] out = new byte[6];
		packInto(out, 0, 6, BigDecimal.valueOf(account), 0);
		return out;
	}

	public static boolean isPacked(byte[] image, int from, int length)
	{
		for (int i = 0; i < length; i++)
		{
			int b = image[from + i] & 0xFF;
			if ((b >> 4) > 9 || (i < length - 1 ? (b & 0x0F) > 9 : (b & 0x0F) < 0x0A))
				return false;
		}
		return true;
	}

	public static BigDecimal packed(byte[] image, int from, int length, int scale)
	{
		long value = 0;
		for (int i = 0; i < length; i++)
		{
			int b = image[from + i] & 0xFF;
			value = value * 10 + (b >> 4);
			if (i < length - 1)
				value = value * 10 + (b & 0x0F);
			else if ((b & 0x0F) == 0x0D || (b & 0x0F) == 0x0B)
				value = -value;
		}
		return BigDecimal.valueOf(value, scale);
	}

	public static int halfword(byte[] image, int from)
	{
		return (short) (((image[from] & 0xFF) << 8) | (image[from + 1] & 0xFF));
	}

	public static String text(byte[] image, int from, int length)
	{
		return new String(image, from, length, HOST);
	}

	private static byte[] rootImage(ResultSet r) throws SQLException
	{
		byte[] image = new byte[100];
		packInto(image, 0, 6, BigDecimal.valueOf(r.getLong(1)), 0);
		put(image, 6, String.format("%09d", r.getLong(2)), 9);
		put(image, 15, r.getString(3), 1);
		put(image, 16, r.getString(4), 10);
		for (int i = 0; i < 4; i++)
			packInto(image, 26 + 6 * i, 6, r.getBigDecimal(5 + i), 2);
		image[50] = (byte) (r.getInt(9) >> 8);
		image[51] = (byte) r.getInt(9);
		image[52] = (byte) (r.getInt(10) >> 8);
		image[53] = (byte) r.getInt(10);
		packInto(image, 54, 6, r.getBigDecimal(11), 2);
		packInto(image, 60, 6, r.getBigDecimal(12), 2);
		put(image, 66, r.getString(13), 34);
		return image;
	}

	private static byte[] childImage(ResultSet r) throws SQLException
	{
		byte[] image = new byte[200];
		packInto(image, 0, 3, BigDecimal.valueOf(r.getLong(2)), 0);
		packInto(image, 3, 5, BigDecimal.valueOf(r.getLong(3)), 0);
		int at = 8, column = 4;
		for (int width : new int[] { 6, 6, 16, 4, 4, 6, 6, 6, 2, 4 })
		{
			put(image, at, r.getString(column++), width);
			at += width;
		}
		put(image, 68, String.format("%06d", r.getInt(column++)), 6);
		packInto(image, 74, 7, r.getBigDecimal(column++), 2);
		packInto(image, 81, 7, r.getBigDecimal(column++), 2);
		put(image, 88, r.getString(column++), 4);
		put(image, 92, r.getString(column++), 3);
		put(image, 95, String.format("%02d", r.getInt(column++)), 2);
		at = 97;
		for (int width : new int[] { 15, 22, 13, 2, 9, 15, 1, 1, 8, 17 })
		{
			put(image, at, r.getString(column++), width);
			at += width;
		}
		return image;
	}

	private static void put(byte[] image, int from, String value, int width)
	{
		String text = value == null ? "" : value;
		text = text.length() >= width ? text.substring(0, width) : text + " ".repeat(width - text.length());
		System.arraycopy(text.getBytes(HOST), 0, image, from, width);
	}

	// Packed decimal, sign C or D.
	private static void packInto(byte[] image, int from, int length, BigDecimal value, int scale)
	{
		BigDecimal v = value == null ? BigDecimal.ZERO : value;
		String digits = v.setScale(scale, RoundingMode.DOWN).unscaledValue().abs().toString();
		digits = "0".repeat(Math.max(0, length * 2 - 1 - digits.length())) + digits;
		digits = digits.substring(digits.length() - (length * 2 - 1));
		for (int i = 0; i < length; i++)
		{
			int high = digits.charAt(2 * i) - '0';
			int low = i == length - 1 ? (v.signum() < 0 ? 0x0D : 0x0C) : digits.charAt(2 * i + 1) - '0';
			image[from + i] = (byte) ((high << 4) | low);
		}
	}
}

package com.cardemo.reference;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

public interface TransactionTypeInsertStore
{
    record Value(
        String trType,
        String trDescription
    ) { }
    record InsertResult(int code, String sqlState) { }

    public static final class Jdbc
    {
        private static final String INSERT = "INSERT INTO \"" + "TRANSACTION_TYPE" + "\" (" +
            "\"" + "TR_TYPE" + "\"," +
            "\"" + "TR_DESCRIPTION" + "\"" +
            ") VALUES (" +
            "?," +
            "?" +
            ")";

        public InsertResult insert(Connection connection, Value value)
        {
            Objects.requireNonNull(connection,"connection");
            Objects.requireNonNull(value,"value");
            try (PreparedStatement statement=connection.prepareStatement(INSERT))
            {
                int parameter=1;
                statement.setObject(parameter++,value.trType());
                statement.setObject(parameter++,value.trDescription());
                if (statement.executeUpdate()!=1)
                    throw new SQLException("Insert did not affect exactly one row");
                return new InsertResult(0,"");
            }
            catch (SQLException failure)
            {
                return mapFailure(failure);
            }
        }

        private static InsertResult mapFailure(SQLException failure)
        {
            String state = failure.getSQLState() == null ? "" : failure.getSQLState();
            return new InsertResult(failure.getErrorCode(), state);
        }
    }

}

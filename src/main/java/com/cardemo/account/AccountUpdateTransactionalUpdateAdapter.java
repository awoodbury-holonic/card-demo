package com.cardemo.account;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.slf4j.LoggerFactory;

@Service
public final class AccountUpdateTransactionalUpdateAdapter implements AccountUpdateAggregateUpdateService
{
	private final javax.sql.DataSource dataSource;
	private final AccountValueJdbcRepository primary;
	private final CustomerValueJdbcRepository secondary;

	public AccountUpdateTransactionalUpdateAdapter(javax.sql.DataSource dataSource,
		AccountValueJdbcRepository primary,
		CustomerValueJdbcRepository secondary)
	{
		this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
		this.primary = Objects.requireNonNull(primary, "primary");
		this.secondary = Objects.requireNonNull(secondary, "secondary");
	}

	@Override
	public Result update(UpdateCommand command)
	{
		UpdateCommand value = Objects.requireNonNull(command, "command");
		try (Connection connection = dataSource.getConnection())
		{
			connection.setAutoCommit(false);
			try
			{
				Optional<AccountValue> currentPrimary =
					primary.findForUpdate(connection,
						value.prior().account().accountId());
				Optional<CustomerValue> currentSecondary =
					secondary.findForUpdate(connection,
						value.prior().customer().customerId());
				if (currentPrimary.isEmpty() || currentSecondary.isEmpty())
				{
					connection.rollback();
					return new Result(Outcome.LOCK_FAILED, null);
				}
				Aggregate current = new Aggregate(currentPrimary.orElseThrow(),
					currentSecondary.orElseThrow());
				if (!current.account().sameAs(
						value.prior().account()) ||
					!current.customer().sameAs(
						value.prior().customer()))
				{
					connection.rollback();
					return new Result(Outcome.STALE, current);
				}
				if (!primary.update(connection,
					value.requested().account()) ||
					!secondary.update(connection,
						value.requested().customer()))
				{
					connection.rollback();
					return new Result(Outcome.FAILED, current);
				}
				connection.commit();
				return new Result(Outcome.UPDATED, value.requested());
			}
			catch (SQLException failure)
			{
				LoggerFactory.getLogger(AccountUpdateTransactionalUpdateAdapter.class).warn("jdbc transactional aggregate update adapter: access failed", failure);
				connection.rollback();
				return new Result(Outcome.FAILED, null);
			}
		}
		catch (SQLException failure)
		{
			LoggerFactory.getLogger(AccountUpdateTransactionalUpdateAdapter.class).warn("jdbc transactional aggregate update adapter: access failed", failure);
			return new Result(Outcome.FAILED, null);
		}
	}
}

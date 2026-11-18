package com.cardemo.authorization;

import java.util.Objects;

public final class AuthorizationDetailQuery<P, K, R, D>
{
	public enum QueryOutcome
	{
		FOUND,
		EXHAUSTED,
		FAILED
	}

	public record QueryKey<P, K>(P primary, K detail)
	{
		public QueryKey
		{
			Objects.requireNonNull(primary, "primary");
			Objects.requireNonNull(detail, "detail");
		}
	}

	public record QueryResult<T>(QueryOutcome outcome, T value,
		String diagnostic)
	{
		public QueryResult
		{
			Objects.requireNonNull(outcome, "outcome");
			diagnostic = diagnostic == null ? "" : diagnostic;
		}
	}

	public interface HierarchicalQueryPort<P, K, R, D>
	{
		QueryResult<R> findRoot(P primary);

		QueryResult<D> findDetail(R root, K detail);
	}

	private final HierarchicalQueryPort<P, K, R, D> query;

	public AuthorizationDetailQuery(HierarchicalQueryPort<P, K, R, D> query)
	{
		this.query = Objects.requireNonNull(query, "query");
	}

	public QueryResult<D> find(QueryKey<P, K> key)
	{
		QueryKey<P, K> checkedKey = Objects.requireNonNull(key, "key");
		QueryResult<R> root = Objects.requireNonNull(
			query.findRoot(checkedKey.primary()), "root query result");
		if (root.outcome() != QueryOutcome.FOUND)
		{
			return new QueryResult<>(root.outcome(), null, root.diagnostic());
		}
		R rootValue = Objects.requireNonNull(root.value(), "root value");
		return Objects.requireNonNull(
			query.findDetail(rootValue, checkedKey.detail()),
			"detail query result");
	}

}

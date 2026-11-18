package com.cardemo.reference;

public interface TransactionTypeRepository<T>
{
	interface TransactionType { }
	void save(TransactionType entity);
	<T> void save(T entity);
	int updateTrDescriptionByTrType(String trDescription, String trType);
	int deleteByTrType(String trType);

}

package com.cardemo.integration;

public interface AccountRecordInquiryService<K, V>
{
	enum InquiryOutcome
	{
		FOUND,
		INVALID_REQUEST,
		FAILED
	}

	record InquiryResult<T>(InquiryOutcome outcome, T value) { }

	InquiryResult<V> inquire(String function, K key);

}

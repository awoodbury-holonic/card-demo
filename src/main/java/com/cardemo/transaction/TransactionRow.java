package com.cardemo.transaction;

public record TransactionRow(
	String id,
	String date,
	String description,
	String amount
) { }

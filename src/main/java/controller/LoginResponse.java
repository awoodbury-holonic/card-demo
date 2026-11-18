package com.cardemo.auth;

import java.util.Objects;

public record LoginResponse(String outcome, String destination,
	String message)
{
	public LoginResponse
	{
		Objects.requireNonNull(outcome, "outcome");
		Objects.requireNonNull(destination, "destination");
		Objects.requireNonNull(message, "message");
	}
}

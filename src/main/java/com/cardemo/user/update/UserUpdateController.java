package com.cardemo.user.update;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user-security/update")
public final class UserUpdateController
{
	public record PublicValue(String userId,
		String firstName, String lastName,
		String userType) { }
	public record Response(String outcome,
		Optional<PublicValue> value,
		String message) { }

	private final SecurityUserUpdateService service;

	public UserUpdateController(SecurityUserUpdateService service)
	{
		this.service = Objects.requireNonNull(service, "service");
	}

	@GetMapping
	public Response find(@RequestParam(name = "userId") String key)
	{
		var result = service.find(key);
		return switch (result.outcome())
		{
			case FOUND -> new Response("FOUND",
				result.value().map(UserUpdateController::publicValue), "");
			case NOT_FOUND -> new Response("NOT_FOUND", Optional.empty(),
				"User not found");
			case FAILED -> new Response("FAILED", Optional.empty(),
				"Unable to lookup user");
		};
	}

	@PostMapping
	public Response update(@RequestBody SecurityUserUpdateService.UserValue request)
	{
		SecurityUserUpdateService.UserValue value =
			Objects.requireNonNull(request, "request");
		var lookup = service.find(value.userId());
		if (lookup.outcome() != SecurityUserUpdateService.LookupOutcome.FOUND)
			return new Response(lookup.outcome().name(), Optional.empty(),
				lookup.outcome() == SecurityUserUpdateService.LookupOutcome.NOT_FOUND
					? "User not found" : "Unable to lookup user");
		List<String> missing = new ArrayList<>(5);
		if (blank(value.userId())) missing.add("userId");
		if (blank(value.firstName())) missing.add("firstName");
		if (blank(value.lastName())) missing.add("lastName");
		if (blank(value.password())) missing.add("password");
		if (blank(value.userType())) missing.add("userType");
		var result = service.update(new SecurityUserUpdateService.UpdateRequest<>(
			lookup.value().orElseThrow(), value, missing));
		return new Response(result.outcome().name(),
			Optional.of(publicValue(result.value())),
			result.outcome() == SecurityUserUpdateService.Outcome.UPDATED
				? "User updated" : "Unable to update user");
	}

	private static PublicValue publicValue(
		SecurityUserUpdateService.UserValue value)
	{
		return new PublicValue(value.userId(),
			value.firstName(), value.lastName(),
			value.userType());
	}

	private static boolean blank(String value)
	{
		return value == null || value.isBlank();
	}
}

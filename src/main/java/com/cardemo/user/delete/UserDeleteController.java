package com.cardemo.user.delete;

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
@RequestMapping("/api/user-security/delete")
public final class UserDeleteController
{
	public record DeleteCommand(String userId) { }
	public record Response(String outcome,
		Optional<SecurityUserDeleteService.UserValue> value,
		String message) { }

	private final SecurityUserDeleteService service;

	public UserDeleteController(SecurityUserDeleteService service)
	{
		this.service = Objects.requireNonNull(service, "service");
	}

	@GetMapping
	public Response find(@RequestParam(name = "userId") String key)
	{
		var result = service.find(key);
		return switch (result.outcome())
		{
			case FOUND -> new Response("FOUND", result.value(), "");
			case NOT_FOUND -> new Response("NOT_FOUND", Optional.empty(),
				"User not found");
			case FAILED -> new Response("FAILED", Optional.empty(),
				"Unable to lookup user");
		};
	}

	@PostMapping
	public Response delete(@RequestBody DeleteCommand command)
	{
		DeleteCommand value = Objects.requireNonNull(command, "command");
		List<String> missing = value.userId() == null ||
			value.userId().isBlank()
			? List.of("userId") : List.of();
		var result = service.delete(new SecurityUserDeleteService.DeleteRequest(
			value.userId(), missing));
		return new Response(result.outcome().name(), result.deletedValue(),
			switch (result.outcome())
			{
				case DELETED -> "User deleted";
				case NOT_FOUND -> "User not found";
				case FAILED -> "Unable to delete user";
			});
	}
}

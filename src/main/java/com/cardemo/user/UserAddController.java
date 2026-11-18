package com.cardemo.user;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user-security/create")
public final class UserAddController
{
	public record CreateResponse(String outcome, String message) { }

	private final SecurityUserCreateService service;

	public UserAddController(SecurityUserCreateService service)
	{
		this.service = Objects.requireNonNull(service, "service");
	}

	@PostMapping
	public CreateResponse create(@RequestBody SecurityUserCreateService.UserValue request)
	{
		SecurityUserCreateService.UserValue value =
			Objects.requireNonNull(request, "request");
		List<String> missing = new ArrayList<>(5);
		if (blank(value.firstName())) missing.add("firstName");
		if (blank(value.lastName())) missing.add("lastName");
		if (blank(value.userId())) missing.add("userId");
		if (blank(value.password())) missing.add("password");
		if (blank(value.userType())) missing.add("userType");
		var result = service.create(new SecurityUserCreateService.CreateRequest<>(
			value, missing));
		return new CreateResponse(result.outcome().name(),
			switch (result.outcome())
			{
				case CREATED -> "User added";
				case DUPLICATE -> "User already exists";
				case FAILED -> "Unable to add user";
			});
	}

	private static boolean blank(String value)
	{
		return value == null || value.isBlank();
	}
}

package com.cardemo.user;

import java.util.Objects;
import java.util.Optional;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public final class UserController
{
	private final UserListPageService service;

	public UserController(UserListPageService service)
	{
		this.service = service;
	}

	@GetMapping
	public UserListPageService.BrowsePage<UserDto,String> browse(
		@RequestParam(name = "startKey") String startKey,
		@RequestParam(name = "direction") UserListPageService.Direction direction,
		@RequestParam(name = "anchorMode") UserListPageService.AnchorMode anchorMode)
	{
		return service.browse(new UserListPageService.KeyedPageQuery<>(
			startKey, direction,
			anchorMode));
	}
	private long cdemoPgmContext;



	public enum TransferContext { RESET }

	public record TransferContinuation(String fromTransactionId,
		String fromProgram, TransferContext context)
	{
		public TransferContinuation
		{
			Objects.requireNonNull(fromTransactionId, "fromTransactionId");
			Objects.requireNonNull(fromProgram, "fromProgram");
			Objects.requireNonNull(context, "context");
		}
	}

	public record TransferNavigation(String destination,
		TransferContinuation continuation)
	{
		public TransferNavigation
		{
			Objects.requireNonNull(destination, "destination");
			Objects.requireNonNull(continuation, "continuation");
			if (destination.isBlank())
				throw new IllegalArgumentException("destination is required");
		}
	}

	public static TransferNavigation planTransfer(
		Optional<String> normalizedDestination,
		String fromTransactionId, String fromProgram)
	{
		String destination = Objects.requireNonNull(normalizedDestination,
			"normalizedDestination").orElse("COSGN00C");
		return new TransferNavigation(destination,
			new TransferContinuation(fromTransactionId, fromProgram,
				TransferContext.RESET));
	}
}

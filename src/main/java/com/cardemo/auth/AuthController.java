package com.cardemo.auth;

import java.util.Objects;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/authentication")
public final class AuthController
{
	public record SignOnRequest(String identifier, String credential)
	{
		public SignOnRequest
		{
			Objects.requireNonNull(identifier, "identifier");
			Objects.requireNonNull(credential, "credential");
		}
	}

	public record SignOnResponse(String outcome, String destination,
		String message)
	{
		public SignOnResponse
		{
			Objects.requireNonNull(outcome, "outcome");
			Objects.requireNonNull(destination, "destination");
			Objects.requireNonNull(message, "message");
		}
	}

	public interface SessionPort
	{
		void establish(String principal, String role);
		void invalidate();
	}

	public interface DestinationPort
	{
		String resolve(String sourceDestination);
	}

	private final AuthService authentication;
	private final SessionPort sessions;
	private final DestinationPort destinations;

	public AuthController(AuthService authentication,
		SessionPort sessions, DestinationPort destinations)
	{
		this.authentication = Objects.requireNonNull(authentication,
			"authentication");
		this.sessions = Objects.requireNonNull(sessions, "sessions");
		this.destinations = Objects.requireNonNull(destinations,
			"destinations");
	}

	@PostMapping
	public SignOnResponse signOn(@RequestBody SignOnRequest request)
	{
		SignOnRequest value = Objects.requireNonNull(request, "request");
		var result = Objects.requireNonNull(authentication.authenticate(
			value.identifier(), value.credential()), "authentication result");
		boolean authenticated = result.outcome() ==
			AuthService.AuthenticationOutcome.AUTHENTICATED;
		if (authenticated)
			sessions.establish(result.principal(), result.role());
		String destination = authenticated
			? Objects.requireNonNull(destinations.resolve(
				result.destination()), "destination") : "";
		return new SignOnResponse(result.outcome().name(), destination,
			Objects.requireNonNull(result.message(), "message"));
	}

	@DeleteMapping("/logout")
	@org.springframework.web.bind.annotation.ResponseStatus(
		org.springframework.http.HttpStatus.NO_CONTENT)
	public void logOut()
	{
		sessions.invalidate();
	}
}

@Component
final class AuthControllerDestinationPort
	implements AuthController.DestinationPort
{
	@Override
	public String resolve(String sourceDestination)
	{
		return switch (Objects.requireNonNull(sourceDestination,
			"sourceDestination"))
		{
			case "COADM01C" -> "/admin";
			case "COMEN01C" -> "/menu";
			default -> throw new IllegalArgumentException(
				"unmapped sign-on destination");
		};
	}
}

@Component
@RequestScope
class AuthControllerSessionPort implements AuthController.SessionPort
{
	private final HttpSession session;

	public AuthControllerSessionPort(HttpSession session)
	{
		this.session = Objects.requireNonNull(session, "session");
	}

	@Override
	public void establish(String principal, String role)
	{
		session.setAttribute("authenticatedPrincipal",
			Objects.requireNonNull(principal, "principal"));
		session.setAttribute("authenticatedRole",
			Objects.requireNonNull(role, "role"));
	}

	@Override
	public void invalidate()
	{
		session.invalidate();
	}
}

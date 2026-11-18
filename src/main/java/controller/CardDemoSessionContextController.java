package com.cardemo;

import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class CardDemoSessionContextController
{
	public record SessionContext(boolean authenticated, String role)
	{
		public SessionContext
		{
			Objects.requireNonNull(role, "role");
		}
	}

	@GetMapping("/api/session-context")
	public SessionContext context(HttpServletRequest request)
	{
		HttpSession session = request.getSession(false);
		Object principalValue = session == null ? null :
			session.getAttribute("authenticatedPrincipal");
		Object roleValue = session == null ? null :
			session.getAttribute("authenticatedRole");
		boolean authenticated = principalValue instanceof String principal &&
			!principal.isBlank();
		return new SessionContext(authenticated,
			roleValue instanceof String role ? role : "");
	}
}

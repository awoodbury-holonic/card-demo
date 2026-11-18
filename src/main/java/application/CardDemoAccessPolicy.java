package com.cardemo;

import java.io.IOException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CardDemoAccessPolicy implements WebMvcConfigurer
{
	public enum Access { PUBLIC, AUTHENTICATED, ADMINISTRATOR }
	public enum Match { EXACT, PREFIX }

	public record RoutePolicy(String path, Access access, Match match)
	{
		public RoutePolicy
		{
			Objects.requireNonNull(path, "path");
			Objects.requireNonNull(access, "access");
			Objects.requireNonNull(match, "match");
			if (!path.startsWith("/") || path.endsWith("/"))
				throw new IllegalArgumentException("route policy path");
		}

		boolean matches(String requestPath)
		{
			return requestPath.equals(path) || (match == Match.PREFIX &&
				requestPath.startsWith(path + "/"));
		}
	}

	public record AccessDecision(boolean allowed, int status, String code)
	{
		public AccessDecision
		{
			Objects.requireNonNull(code, "code");
			if (allowed != (status == 200 && code.isEmpty()))
				throw new IllegalArgumentException("access decision");
		}
	}

	private static final List<RoutePolicy> ROUTES = List.of(
		new RoutePolicy("/api/authentication", Access.PUBLIC, Match.PREFIX),
		new RoutePolicy("/api/session-context", Access.PUBLIC, Match.EXACT),
		new RoutePolicy("/api/user-security", Access.ADMINISTRATOR, Match.PREFIX),
		new RoutePolicy("/api/transaction-types", Access.ADMINISTRATOR, Match.PREFIX),
		new RoutePolicy("/api/users", Access.ADMINISTRATOR, Match.PREFIX)
);

	static
	{
		if (ROUTES.size() != 5)
			throw new IllegalStateException("duplicate governed access route");
		Set<String> identities = new HashSet<>();
		for (RoutePolicy policy : ROUTES)
			if (!identities.add(policy.match() + ":" + policy.path()))
				throw new IllegalStateException("duplicate governed access route");
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry)
	{
		registry.addInterceptor(new Guard()).addPathPatterns("/api/**");
	}

	private static Access accessFor(String requestPath)
	{
		return ROUTES.stream().filter(policy -> policy.matches(requestPath))
			.max(Comparator.comparingInt(policy -> policy.path().length()))
			.map(RoutePolicy::access).orElse(Access.AUTHENTICATED);
	}

	static AccessDecision decide(String requestPath, String principal, String role)
	{
		Access access = accessFor(Objects.requireNonNull(requestPath,
			"requestPath"));
		if (access == Access.PUBLIC)
			return new AccessDecision(true, 200, "");
		if (principal == null || principal.isBlank())
			return new AccessDecision(false, 401,
				"unauthenticated");
		if (access == Access.ADMINISTRATOR &&
			!"A".equals(role))
			return new AccessDecision(false, 403, "forbidden");
		return new AccessDecision(true, 200, "");
	}

	private static final class Guard implements HandlerInterceptor
	{
		@Override
		public boolean preHandle(HttpServletRequest request,
			HttpServletResponse response, Object handler) throws IOException
		{
			String path = Objects.requireNonNull(request.getRequestURI(),
				"request path");
			HttpSession session = request.getSession(false);
			Object principalValue = session == null ? null :
				session.getAttribute("authenticatedPrincipal");
			Object roleValue = session == null ? null :
				session.getAttribute("authenticatedRole");
			AccessDecision decision = decide(path,
				principalValue instanceof String principal ? principal : null,
				roleValue instanceof String role ? role : null);
			return decision.allowed() || reject(response, decision.status(),
				decision.code());
		}

		private static boolean reject(HttpServletResponse response, int status,
			String code) throws IOException
		{
			response.setStatus(status);
			response.setContentType("application/json");
			response.getWriter().write("{\"error\":\"" + code +
				"\",\"status\":" + status + "}");
			return false;
		}
	}
}

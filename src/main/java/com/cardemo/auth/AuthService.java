package com.cardemo.auth;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import application.UserIdentityPolicy;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.slf4j.LoggerFactory;

@Service
public final class AuthService
{
	public enum AuthenticationOutcome
	{
		AUTHENTICATED,
		INVALID_CREDENTIALS,
		UNKNOWN_PRINCIPAL,
		UNAVAILABLE
	}

	public record AuthenticationResult(AuthenticationOutcome outcome,
		String principal, String role, String destination, String message)
	{
		public AuthenticationResult
		{
			Objects.requireNonNull(outcome, "outcome");
			Objects.requireNonNull(principal, "principal");
			Objects.requireNonNull(role, "role");
			Objects.requireNonNull(destination, "destination");
			Objects.requireNonNull(message, "message");
		}
	}

	public enum LookupOutcome { FOUND, UNKNOWN_PRINCIPAL, UNAVAILABLE }

	public record CredentialRecord(String storedPrincipal, String storedCredential, String role)
	{
		public CredentialRecord
		{
			Objects.requireNonNull(storedPrincipal, "storedPrincipal");
			Objects.requireNonNull(storedCredential,
				"storedCredential");
			Objects.requireNonNull(role, "role");
		}
	}

	public record LookupResult(LookupOutcome outcome,
		Optional<CredentialRecord> record)
	{
		public LookupResult
		{
			Objects.requireNonNull(outcome, "outcome");
			record = Objects.requireNonNull(record, "record");
			if ((outcome == LookupOutcome.FOUND) != record.isPresent())
				throw new IllegalArgumentException("lookup outcome/record mismatch");
		}
	}

	public interface CredentialRepository
	{
		LookupResult lookup(String principal);
	}

	private final CredentialRepository repository;

	public AuthService(CredentialRepository repository)
	{
		this.repository = Objects.requireNonNull(repository,
			"repository");
	}

	public AuthenticationResult authenticate(String principal, String credential)
	{
		String suppliedPrincipal = Objects.requireNonNull(principal,
			"principal");
		String suppliedCredential = Objects.requireNonNull(credential,
			"credential");
		LookupResult lookup = Objects.requireNonNull(
			repository.lookup(suppliedPrincipal), "lookup");
		if (lookup.outcome() == LookupOutcome.UNKNOWN_PRINCIPAL)
			return new AuthenticationResult(AuthenticationOutcome.UNKNOWN_PRINCIPAL,
				"", "", "", "User not found. Try again ...");
		if (lookup.outcome() == LookupOutcome.UNAVAILABLE)
			return new AuthenticationResult(AuthenticationOutcome.UNAVAILABLE,
				"", "", "", "Unable to verify the User ...");
		CredentialRecord found = lookup.record().orElseThrow();
		if (!UserIdentityPolicy.passwordMatches(found.storedCredential(), suppliedCredential))
			return new AuthenticationResult(
				AuthenticationOutcome.INVALID_CREDENTIALS, "", "", "",
				"Wrong Password. Try again ...");
		String destination = found.role().equals("A")
			? "COADM01C" : "COMEN01C";
		return new AuthenticationResult(AuthenticationOutcome.AUTHENTICATED,
			found.storedPrincipal(), found.role(), destination, "");
	}
}

@Repository
class AuthServiceRelationalRepository
	implements AuthService.CredentialRepository
{
	private static final String LOOKUP_SQL =
		"SELECT SEC_USR_PWD, SEC_USR_TYPE, SEC_USR_ID " +
		"FROM USRSEC WHERE " + UserIdentityPolicy.keySql("SEC_USR_ID") + " = ?";

	private final javax.sql.DataSource dataSource;

	public AuthServiceRelationalRepository(javax.sql.DataSource dataSource)
	{
		this.dataSource = Objects.requireNonNull(dataSource,
			"dataSource");
	}

	@Override
	public AuthService.LookupResult lookup(String principal)
	{
		Objects.requireNonNull(principal, "principal");
		try (Connection connection = dataSource.getConnection();
			PreparedStatement statement =
				connection.prepareStatement(LOOKUP_SQL))
		{
			statement.setString(1, principal);
			try (ResultSet rows = statement.executeQuery())
			{
				if (!rows.next())
					return new AuthService.LookupResult(
						AuthService.LookupOutcome.UNKNOWN_PRINCIPAL,
						Optional.empty());
				String storedPrincipal = Objects.requireNonNull(
					rows.getString(3), "storedPrincipal").stripTrailing();
				String storedCredential = Objects.requireNonNull(
					rows.getString(1), "storedCredential");
				String role = Objects.requireNonNull(rows.getString(2),
					"role").stripTrailing();
				if (rows.next())
					return unavailable();
				return new AuthService.LookupResult(
					AuthService.LookupOutcome.FOUND,
					Optional.of(new AuthService.CredentialRecord(
						storedPrincipal, storedCredential, role)));
			}
		}
		catch (SQLException | NullPointerException failure)
		{
			LoggerFactory.getLogger(AuthService.class).warn("relational normalized authentication service: lookup failed", failure);
			return unavailable();
		}
	}

	private static AuthService.LookupResult unavailable()
	{
		return new AuthService.LookupResult(
			AuthService.LookupOutcome.UNAVAILABLE,
			Optional.empty());
	}
}

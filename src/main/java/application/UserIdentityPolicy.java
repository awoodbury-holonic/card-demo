package application;

import java.util.Objects;

/** Web identity policy shared by authentication and user-maintenance queries. */
public final class UserIdentityPolicy
{
	private UserIdentityPolicy() { }

	public static String keySql(String trustedColumn)
	{
		// RTRIM returns ordinary VARCHAR in H2; restore the identity column's
		// case-insensitive type after removing fixed-record padding.
		return "CAST(RTRIM(" + trustedColumn + ") AS VARCHAR_IGNORECASE)";
	}

	public static boolean passwordMatches(String stored, String supplied)
	{
		return Objects.requireNonNull(stored, "stored").equals(
			Objects.requireNonNull(supplied, "supplied"));
	}
}

package com.cardemo.user;

public record UserDto(
	String userId,
	String firstName,
	String lastName,
	String userType
)
{
	public static UserDto fromRecord(SecurityUser record)
	{
		return new UserDto(
			record.getSecUsrId(),
			record.getSecUsrFname(),
			record.getSecUsrLname(),
			record.getSecUsrType()
		);
	}
}

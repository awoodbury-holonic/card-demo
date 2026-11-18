package com.cardemo.integration;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public final class CurrentDateTimeService
{
	public record DateTimeReply(String date, String time)
	{
		public DateTimeReply
		{
			Objects.requireNonNull(date, "date");
			Objects.requireNonNull(time, "time");
		}
	}

	public interface ClockPort
	{
		DateTimeReply current();
	}

	private final ClockPort clock;

	public CurrentDateTimeService(ClockPort clock)
	{
		this.clock = Objects.requireNonNull(clock, "clock");
	}

	public DateTimeReply current()
	{
		return Objects.requireNonNull(clock.current(),
			"current date/time");
	}

	public static ClockPort systemClock()
	{
		ZoneId zone = ZoneId.of("UTC");
		DateTimeFormatter date =
			DateTimeFormatter.ofPattern("MM-dd-uuuu");
		DateTimeFormatter time =
			DateTimeFormatter.ofPattern("HH:mm:ss");
		return () ->
		{
			ZonedDateTime now = ZonedDateTime.now(zone);
			return new DateTimeReply(date.format(now), time.format(now));
		};
	}
}

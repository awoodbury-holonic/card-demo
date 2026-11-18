package com.cardemo.integration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

// One REST request is one invocation of the source request/reply program.
@RestController
public final class DateTimeController
{
	@PostMapping(value = "/api/mq/date", produces = "text/plain")
	public String reply()
	{
		LocalDateTime now = LocalDateTime.now();
		String value = "SYSTEM DATE : " + now.format(DateTimeFormatter.ofPattern("MM-dd-yyyy"))
			+ "SYSTEM TIME : " + now.format(DateTimeFormatter.ofPattern("HH:mm:ss"));
		return value + " ".repeat(1000 - value.length());
	}
}

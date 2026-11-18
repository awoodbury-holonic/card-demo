package com.cardemo;

import java.util.Objects;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(basePackages = "com.cardemo")
@Import({ com.cardemo.auth.AuthController.class,com.cardemo.auth.AuthService.class,CardDemoAccessPolicy.class,CardDemoSessionContextController.class })
public class CardemoApplication
{
	public CardemoApplication()
	{
	}

	public static void main(String[] arguments)
	{
		SpringApplication application = new SpringApplication(CardemoApplication.class);
		var context = application.run(Objects.requireNonNull(arguments, "arguments"));
		// A batch invocation must report failure to its scheduler. Web mode
		// keeps its context running; only an explicitly enabled non-web job exits.
		if (application.getWebApplicationType() == WebApplicationType.NONE &&
			context.getEnvironment().getProperty("spring.batch.job.enabled", Boolean.class, false))
			System.exit(SpringApplication.exit(context));
	}
}

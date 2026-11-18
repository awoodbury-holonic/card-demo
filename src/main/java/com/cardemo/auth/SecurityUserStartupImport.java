package com.cardemo.auth;

import java.nio.file.Path;
import java.util.Objects;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

// Seed imports run before anything else at startup, so a batch job launched
// at startup (by name, or by its startup flag) sees the imported files.
@Service
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class SecurityUserStartupImport implements ApplicationRunner
{
	private final SecurityUserImportService importer;
	private final boolean enabled;
	private final Path projectRoot;

	public SecurityUserStartupImport(SecurityUserImportService importer,
		Environment environment)
	{
		this.importer = Objects.requireNonNull(importer, "importer");
		Environment properties = Objects.requireNonNull(environment,
			"environment");
		this.enabled = properties.getProperty(
			"carddemo.import.usrsec.enabled", Boolean.class, false);
		this.projectRoot = Path.of(
			properties.getProperty("carddemo.project-root", "."));
	}

	@Override
	public void run(ApplicationArguments arguments) throws Exception
	{
		Objects.requireNonNull(arguments, "arguments");
		if (enabled)
			importer.importFrom(projectRoot);
	}
}

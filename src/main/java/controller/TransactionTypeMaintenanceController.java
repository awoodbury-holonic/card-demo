package com.cardemo.reference;

import java.util.Objects;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transaction-types/maintenance")
public final class TransactionTypeMaintenanceController
{
	private final TransactionTypeMaintenanceService service;

	public TransactionTypeMaintenanceController(TransactionTypeMaintenanceService service)
	{
		this.service = Objects.requireNonNull(service, "service");
	}

	@PostMapping
	public TransactionTypeMaintenanceService.Result execute(@RequestBody TransactionTypeMaintenanceService.Command command)
	{
		return service.execute(command);
	}
}

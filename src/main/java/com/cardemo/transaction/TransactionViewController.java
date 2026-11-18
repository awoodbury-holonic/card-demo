package com.cardemo.transaction;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/transactions")
public final class TransactionViewController
{
	private final DetailLookupPort detailRepository;

	public TransactionViewController(DetailLookupPort detailRepository)
	{
		this.detailRepository = Objects.requireNonNull(
			detailRepository, "detailRepository");
	}

	@GetMapping("/{transactionId}")
	public ResponseEntity<TransactionDetail> findTransaction(
		@PathVariable("transactionId") String transactionId)
	{
		DetailLookupResult result = inquire(transactionId, detailRepository);
		return switch (result.outcome())
		{
			case FOUND -> ResponseEntity.ok(result.detail().orElseThrow());
			case NOT_FOUND -> ResponseEntity.notFound().build();
			case FAILED -> ResponseEntity.internalServerError().build();
		};
	}
	private long cdemoPgmContext;



	public record TransactionDetail(
		String transactionId,
		String typeCode,
		long categoryCode,
		String source,
		String description,
		BigDecimal amount,
		long merchantId,
		String merchantName,
		String merchantCity,
		String merchantZip,
		String cardNumber,
		@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSSSSS") LocalDateTime originalTimestamp,
		@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss.SSSSSS") LocalDateTime processedTimestamp
	) { }

	public enum DetailLookupOutcome { FOUND, NOT_FOUND, FAILED }

	public record DetailLookupResult(DetailLookupOutcome outcome,
		Optional<TransactionDetail> detail, String message)
	{
		public DetailLookupResult
		{
			Objects.requireNonNull(outcome, "outcome");
			detail = Objects.requireNonNull(detail, "detail");
			Objects.requireNonNull(message, "message");
			if ((outcome == DetailLookupOutcome.FOUND) != detail.isPresent())
				throw new IllegalArgumentException("detail must match lookup outcome");
		}

		public static DetailLookupResult notFound()
		{
			return new DetailLookupResult(DetailLookupOutcome.NOT_FOUND,
				Optional.empty(), "Transaction ID NOT found...");
		}

		public static DetailLookupResult failed()
		{
			return new DetailLookupResult(DetailLookupOutcome.FAILED,
				Optional.empty(), "Unable to lookup Transaction...");
		}
	}

	public interface DetailLookupPort
	{
		DetailLookupResult find(String key);
	}

	public static DetailLookupResult inquire(String key,
		DetailLookupPort repository)
	{
		return Objects.requireNonNull(repository, "repository").find(key);
	}

	public enum DetailVisit { INITIAL_SIGN_ON, FIRST_PRESENTATION, RECEIVED_EVENT }
	public enum DetailEvent { ENTER, BACK, CLEAR, LIST, OTHER }
	public enum DetailAction { ROUTE, PRESENT }
	public enum FormState { FRESH }

	public record DetailRequest(DetailVisit visit, DetailEvent event,
		String transactionId, Optional<String> previousProgram,
		Optional<String> selectedTransaction)
	{
		public DetailRequest
		{
			Objects.requireNonNull(visit, "visit");
			Objects.requireNonNull(event, "event");
			Objects.requireNonNull(transactionId, "transactionId");
			previousProgram = Objects.requireNonNull(previousProgram, "previousProgram");
			selectedTransaction = Objects.requireNonNull(selectedTransaction, "selectedTransaction");
		}
	}

	public record DetailResponse(DetailAction action,
		Optional<String> destination, FormState formState,
		Optional<DetailLookupResult> lookup, String message)
	{
		public DetailResponse
		{
			Objects.requireNonNull(action, "action");
			destination = Objects.requireNonNull(destination, "destination");
			Objects.requireNonNull(formState, "formState");
			lookup = Objects.requireNonNull(lookup, "lookup");
			Objects.requireNonNull(message, "message");
			if ((action == DetailAction.ROUTE) != destination.isPresent())
				throw new IllegalArgumentException("destination must match action");
		}

		public static DetailResponse route(String target)
		{
			return new DetailResponse(DetailAction.ROUTE,
				Optional.of(target), FormState.FRESH,
				Optional.empty(), "");
		}

		public static DetailResponse present(Optional<DetailLookupResult> result,
			String message)
		{
			return new DetailResponse(DetailAction.PRESENT,
				Optional.empty(), FormState.FRESH, result, message);
		}
	}

	public static DetailResponse handleDetailRequest(DetailRequest request,
		DetailLookupPort repository)
	{
		DetailRequest value = Objects.requireNonNull(request, "request");
		DetailLookupPort lookup = Objects.requireNonNull(repository, "repository");
		return switch (value.visit())
		{
			case INITIAL_SIGN_ON -> DetailResponse.route("COSGN00C");
			case FIRST_PRESENTATION -> value.selectedTransaction()
				.map(key -> presentLookup(inquire(key, lookup)))
				.orElseGet(() -> DetailResponse.present(Optional.empty(), ""));
			case RECEIVED_EVENT -> switch (value.event())
			{
				case ENTER -> value.transactionId().isBlank()
					? DetailResponse.present(Optional.empty(), "Tran ID can NOT be empty...")
					: presentLookup(inquire(value.transactionId(), lookup));
				case BACK -> DetailResponse.route(value.previousProgram()
					.filter(previous -> !previous.isBlank()).orElse("COMEN01C"));
				case CLEAR -> DetailResponse.present(Optional.empty(), "");
				case LIST -> DetailResponse.route("COTRN00C");
				case OTHER -> DetailResponse.present(Optional.empty(),
					"Invalid key pressed. Please see below...");
			};
		};
	}

	private static DetailResponse presentLookup(DetailLookupResult result)
	{
		DetailLookupResult value = Objects.requireNonNull(result, "result");
		return DetailResponse.present(Optional.of(value), value.message());
	}

	public enum TransferRole { INITIAL_ENTRY }

	public record DetailTransferResponse(DetailResponse detail,
		Optional<TransferNavigation> navigation,
		Optional<TransferRole> role)
	{
		public DetailTransferResponse
		{
			Objects.requireNonNull(detail, "detail");
			navigation = Objects.requireNonNull(navigation, "navigation");
			role = Objects.requireNonNull(role, "role");
			if (navigation.isPresent() != role.isPresent())
				throw new IllegalArgumentException("transfer role must match navigation");
			if (detail.destination().isPresent() != navigation.isPresent())
				throw new IllegalArgumentException("transfer must match detail destination");
			if (navigation.isPresent() &&
				!detail.destination().orElseThrow().equals(navigation.orElseThrow().destination()))
				throw new IllegalArgumentException("transfer destination must match detail");
		}
	}

	public static DetailTransferResponse handleDetailTransferRequest(DetailRequest request,
		DetailLookupPort repository)
	{
		DetailResponse detail = handleDetailRequest(request, repository);
		Optional<TransferNavigation> navigation = detail.destination()
			.map(destination -> planTransfer(Optional.of(destination),
				"CT01", "COTRN01C"));
		return new DetailTransferResponse(detail, navigation,
			navigation.map(ignored -> TransferRole.INITIAL_ENTRY));
	}

	public enum TransferContext { RESET }

	public record TransferContinuation(String fromTransactionId,
		String fromProgram, TransferContext context)
	{
		public TransferContinuation
		{
			Objects.requireNonNull(fromTransactionId, "fromTransactionId");
			Objects.requireNonNull(fromProgram, "fromProgram");
			Objects.requireNonNull(context, "context");
		}
	}

	public record TransferNavigation(String destination,
		TransferContinuation continuation)
	{
		public TransferNavigation
		{
			Objects.requireNonNull(destination, "destination");
			Objects.requireNonNull(continuation, "continuation");
			if (destination.isBlank())
				throw new IllegalArgumentException("destination is required");
		}
	}

	public static TransferNavigation planTransfer(
		Optional<String> normalizedDestination,
		String fromTransactionId, String fromProgram)
	{
		String destination = Objects.requireNonNull(normalizedDestination,
			"normalizedDestination").orElse("COSGN00C");
		return new TransferNavigation(destination,
			new TransferContinuation(fromTransactionId, fromProgram,
				TransferContext.RESET));
	}
}

@Component
final class TransactionViewControllerRelationalRepository
	implements TransactionViewController.DetailLookupPort
{
	private static final String LOOKUP_SQL =
		"SELECT TRAN_ID,TRAN_TYPE_CD,TRAN_CAT_CD,TRAN_SOURCE,TRAN_DESC,TRAN_AMT,TRAN_MERCHANT_ID,TRAN_MERCHANT_NAME,TRAN_MERCHANT_CITY,TRAN_MERCHANT_ZIP,TRAN_CARD_NUM,TRAN_ORIG_TS,TRAN_PROC_TS FROM TRANSACT "
		+ "WHERE RTRIM(TRAN_ID) = ?";

	private final javax.sql.DataSource dataSource;

	public TransactionViewControllerRelationalRepository(javax.sql.DataSource dataSource)
	{
		this.dataSource = Objects.requireNonNull(dataSource,
			"dataSource");
	}

	@Override
	public TransactionViewController.DetailLookupResult find(String key)
	{
		Objects.requireNonNull(key, "key");
		try (Connection connection = dataSource.getConnection();
			PreparedStatement statement =
				connection.prepareStatement(LOOKUP_SQL))
		{
			statement.setString(1, key);
			try (ResultSet row = statement.executeQuery())
			{
				if (!row.next()) return TransactionViewController.DetailLookupResult.notFound();
				TransactionViewController.TransactionDetail detail =
					new TransactionViewController.TransactionDetail(
						requiredTrim(row, "TRAN_ID"),
						requiredTrim(row, "TRAN_TYPE_CD"),
						requiredLong(row, "TRAN_CAT_CD"),
						requiredStrip(row, "TRAN_SOURCE"),
						requiredStrip(row, "TRAN_DESC"),
						requiredDecimal(row, "TRAN_AMT"),
						requiredLong(row, "TRAN_MERCHANT_ID"),
						requiredStrip(row, "TRAN_MERCHANT_NAME"),
						requiredStrip(row, "TRAN_MERCHANT_CITY"),
						requiredTrim(row, "TRAN_MERCHANT_ZIP"),
						requiredTrim(row, "TRAN_CARD_NUM"),
						requiredTimestamp(row, "TRAN_ORIG_TS"),
						requiredTimestamp(row, "TRAN_PROC_TS")
					);
				if (row.next()) return TransactionViewController.DetailLookupResult.failed();
				return new TransactionViewController.DetailLookupResult(
					TransactionViewController.DetailLookupOutcome.FOUND,
					Optional.of(detail), "");
			}
		}
		catch (SQLException | NullPointerException failure)
		{
			LoggerFactory.getLogger(TransactionViewController.class).warn("relational semantic detail controller: find failed", failure);
			return TransactionViewController.DetailLookupResult.failed();
		}
	}

	private static String requiredTrim(ResultSet row, String column)
		throws SQLException
	{
		return required(row, column).trim();
	}

	private static String requiredStrip(ResultSet row, String column)
		throws SQLException
	{
		return required(row, column).stripTrailing();
	}

	private static long requiredLong(ResultSet row, String column)
		throws SQLException
	{
		return row.getLong(column);
	}

	private static BigDecimal requiredDecimal(ResultSet row,
		String column) throws SQLException
	{
		BigDecimal value = row.getBigDecimal(column);
		if (value == null) throw new SQLException(column + " is null");
		return value;
	}

	// A timestamp column holds a real SQL TIMESTAMP, not the source text: the
	// source writes it in several formats, each converted where it is written.
	private static LocalDateTime requiredTimestamp(
		ResultSet row, String column) throws SQLException
	{
		LocalDateTime value =
			row.getObject(column, LocalDateTime.class);
		if (value == null) throw new SQLException(column + " is null");
		return value;
	}

	private static String required(ResultSet row, String column)
		throws SQLException
	{
		String value = row.getString(column);
		if (value == null) throw new SQLException(column + " is null");
		return value;
	}
}

package com.cardemo.batch;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Objects;
import org.slf4j.LoggerFactory;

public interface CustomerFileListingCustfileFileRepository
{
	enum AccessOutcome
	{
		COMPLETED,
		REJECTED
	}

	record AccessResult<T>(T value, AccessOutcome outcome, String status)
	{
		public AccessResult(T value, AccessOutcome outcome)
		{
			this(value, outcome, "");
		}
	}

	AccessResult<Void> openCustfileFile();
	AccessResult<Void> closeCustfileFile();
	AccessResult<Void> close();
	enum ReadOutcome
	{
		RECORD,
		END_OF_DATA,
		FAILED
	}

	record ReadResult<T>(T value, String recordImage, ReadOutcome outcome, String status)
	{
		public ReadResult(T value, String recordImage, ReadOutcome outcome)
		{
			this(value, recordImage, outcome, "");
		}
	}

	ReadResult<Customer> readNext();
	// DISPLAY-only consumers preserve the source image without validating
	// fields that the source program does not inspect.
	ReadResult<String> readNextImage();

	static CustomerFileListingCustfileFileRepository local(Path projectRoot)
	{
		return new CustomerFileListingCustfileFileRepositoryLocalRepository(projectRoot,
			CustomerFileListingCustfileFileRepositoryLocalRepository.statusPolicy("35|41|47|42|30"));
	}
	int KEY_OFFSET = 0;
	int KEY_LENGTH = 9;
	default long keyOf(Customer record)
	{
		return record.getCustId();
	}

}

final class CustomerFileListingCustfileFileRepositoryLocalRecordSource implements AutoCloseable
{
	private final InputStream input;
	private final Charset charset;

	public CustomerFileListingCustfileFileRepositoryLocalRecordSource(Path projectRoot)
		throws IOException
	{
		Objects.requireNonNull(projectRoot, "projectRoot");
		try
		{
			this.charset = Charset.forName(
				"IBM037");
		}
		catch (IllegalArgumentException failure)
		{
			throw new IOException("unsupported host charset", failure);
		}
		this.input = getClass().getClassLoader()
			.getResourceAsStream("seed-data/AWS.M2.CARDDEMO.CUSTDATA.PS");
		if (input == null)
			throw new NoSuchFileException(
				"seed-data/AWS.M2.CARDDEMO.CUSTDATA.PS");
	}

	public String readNextImage() throws IOException
	{
		byte[] bytes = input.readNBytes(Customer.RECORD_LENGTH);
		if (bytes.length == 0)
			return null;
		if (bytes.length != Customer.RECORD_LENGTH)
			throw new EOFException("incomplete fixed-width record image");
		return new String(bytes, charset);
	}

	@Override
	public void close() throws IOException
	{
		input.close();
	}
}

final class CustomerFileListingCustfileFileRepositoryLocalRepository implements CustomerFileListingCustfileFileRepository
{
	public enum FileOperation
	{
		OPEN,
		READ,
		CLOSE
	}

	@FunctionalInterface
	public interface RecordDecoder<T>
	{
		T decode(String recordImage) throws IOException;
	}

	@FunctionalInterface
	public interface FailureStatus
	{
		String status(FileOperation operation, IOException failure);
	}

	private record HostStatuses(String missingSource, String alreadyOpen,
		String readNotOpen, String closeNotOpen, String permanent) { }

	private static final class AlreadyOpen extends IOException
	{
		private AlreadyOpen() { super("already open"); }
	}

	private static final class NotOpen extends IOException
	{
		private NotOpen() { super("not open"); }
	}

	public static FailureStatus statusPolicy(String specification)
	{
		if (specification == null)
			throw new IllegalArgumentException("host file-status policy is required");
		String[] parts = specification.split("\\|", -1);
		if (parts.length != 5)
			throw new IllegalArgumentException("host file-status policy needs five codes");
		for (String code : parts)
			if (code.length() != 2 || code.charAt(0) < '0' ||
				code.charAt(0) > '9' || code.charAt(1) < '0' ||
				code.charAt(1) > '9')
				throw new IllegalArgumentException("host file-status codes must be numeric pairs");
		HostStatuses codes = new HostStatuses(parts[0], parts[1], parts[2],
			parts[3], parts[4]);
		return (operation, failure) -> {
			if (operation == FileOperation.OPEN &&
				failure instanceof NoSuchFileException)
				return codes.missingSource();
			if (failure instanceof AlreadyOpen)
				return codes.alreadyOpen();
			if (failure instanceof NotOpen)
				return operation == FileOperation.CLOSE ? codes.closeNotOpen() :
					codes.readNotOpen();
			return codes.permanent();
		};
	}

	private final Path projectRoot;
	private final RecordDecoder<Customer> decoder;
	private final FailureStatus failureStatus;
	private CustomerFileListingCustfileFileRepositoryLocalRecordSource source;

	public CustomerFileListingCustfileFileRepositoryLocalRepository(Path projectRoot,
		FailureStatus failureStatus)
	{
		this(projectRoot, Customer::decode, failureStatus);
	}

	public CustomerFileListingCustfileFileRepositoryLocalRepository(Path projectRoot,
		RecordDecoder<Customer> decoder, FailureStatus failureStatus)
	{
		this.projectRoot = Objects.requireNonNull(projectRoot);
		this.decoder = Objects.requireNonNull(decoder);
		this.failureStatus = Objects.requireNonNull(failureStatus);
	}

	@Override
	public AccessResult<Void> openCustfileFile()
	{
		if (source != null)
			return failedAccess(FileOperation.OPEN, new AlreadyOpen());
		try
		{
			source = new CustomerFileListingCustfileFileRepositoryLocalRecordSource(projectRoot);
			return new AccessResult<>(null, AccessOutcome.COMPLETED, "");
		}
		catch (IOException failure)
		{
			LoggerFactory.getLogger(CustomerFileListingCustfileFileRepository.class).warn("local record image source: statusPolicy failed", failure);
			return failedAccess(FileOperation.OPEN, failure);
		}
	}

	@Override
	public ReadResult<Customer> readNext()
	{
		ReadResult<String> result = readNextImage();
		if (result.outcome() != ReadOutcome.RECORD)
			return new ReadResult<>(null, result.recordImage(), result.outcome(), result.status());
		try
		{
			return new ReadResult<>(decoder.decode(result.recordImage()), result.recordImage(),
				ReadOutcome.RECORD, "");
		}
		catch (IOException failure)
		{
			LoggerFactory.getLogger(CustomerFileListingCustfileFileRepository.class).warn("local record image source: readNext failed", failure);
			return failedRead(failure);
		}
	}

	@Override
	public ReadResult<String> readNextImage()
	{
		if (source == null)
			return failedRead(new NotOpen());
		try
		{
			String image = source.readNextImage();
			if (image == null)
				return new ReadResult<>(null, null, ReadOutcome.END_OF_DATA, "");
			return new ReadResult<>(image, image,
				ReadOutcome.RECORD, "");
		}
		catch (IOException failure)
		{
			LoggerFactory.getLogger(CustomerFileListingCustfileFileRepository.class).warn("local record image source: readNext failed", failure);
			return failedRead(failure);
		}
	}

	@Override
	public AccessResult<Void> closeCustfileFile()
	{
		return close();
	}

	@Override
	public AccessResult<Void> close()
	{
		if (source == null)
			return failedAccess(FileOperation.CLOSE, new NotOpen());
		try
		{
			source.close();
			return new AccessResult<>(null, AccessOutcome.COMPLETED, "");
		}
		catch (IOException failure)
		{
			LoggerFactory.getLogger(CustomerFileListingCustfileFileRepository.class).warn("local record image source: close failed", failure);
			return failedAccess(FileOperation.CLOSE, failure);
		}
		finally
		{
			source = null;
		}
	}

	private AccessResult<Void> failedAccess(FileOperation operation,
		IOException failure)
	{
		return new AccessResult<>(null, AccessOutcome.REJECTED,
			failureStatus.status(operation, failure));
	}

	private <T> ReadResult<T> failedRead(
		IOException failure)
	{
		return new ReadResult<>(null, null, ReadOutcome.FAILED,
			failureStatus.status(FileOperation.READ, failure));
	}
}

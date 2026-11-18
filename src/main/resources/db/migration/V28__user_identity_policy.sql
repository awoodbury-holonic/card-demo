-- Web-only identity policy: case-insensitive names, case-sensitive passwords.
-- The existing primary key also rejects names differing only by case.
ALTER TABLE USRSEC ALTER COLUMN SEC_USR_ID
    VARCHAR_IGNORECASE(8) NOT NULL;

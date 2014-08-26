package com.github.mlueders.gradle.ftp.tasks

/**
 * Represents one of the valid timestamp adjustment values
 *
 * A timestamp adjustment may be used in file transfers for checking
 * uptodateness. MINUTE means to add one minute to the server
 * timestamp.  This is done because FTP servers typically list
 * timestamps HH:mm and client FileSystems typically use HH:mm:ss.
 *
 * The default is to use MINUTE for PUT actions and NONE for GET
 * actions, since GETs have the <code>preserveLastModified</code>
 * option, which takes care of the problem in most use cases where
 * this level of granularity is an issue.
 */
public enum TimestampGranularity {
	NONE(0L),
	MINUTE(60000L)

	private milliseconds

	private TimestampGranularity(long milliseconds) {
		this.milliseconds = milliseconds
	}

	public long getMilliseconds() {
		milliseconds
	}

}

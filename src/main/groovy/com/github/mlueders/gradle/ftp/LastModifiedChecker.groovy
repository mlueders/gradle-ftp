package com.github.mlueders.gradle.ftp

import groovy.util.logging.Slf4j
import java.text.SimpleDateFormat
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import org.gradle.api.GradleException

@Slf4j
class LastModifiedChecker {

	/** Date formatter used in logging, note not thread safe! */
	private static final SimpleDateFormat TIMESTAMP_LOGGING_SDF =
			new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

	private static String formatDate(long timestamp) {
		synchronized (TIMESTAMP_LOGGING_SDF) {
			TIMESTAMP_LOGGING_SDF.format(new Date(timestamp))
		}
	}


	/**
	 * adjust uptodate calculations where server timestamps are HH:mm and client's are HH:mm:ss
	 */
	long granularityMillis = 0L
	/**
	 * Automatically determine the time difference between local and remote machine, defaults to false.
	 *
	 * This requires right to create and delete a temporary file in the remote directory.
	 */
	boolean timeDiffAuto = false

	private FTPClient ftp
	/**
	 * Number of milliseconds to add to the time on the remote machine to get the time on the local machine.
	 * Use in conjunction with <code>newerOnly</code>
	 */
	private Long timeDiffMillis = null

	public LastModifiedChecker(FTPClient ftp) {
		this.ftp = ftp
	}

	void initialize() {
		timeDiffMillis = timeDiffAuto ? calculateTimeDiff() : 0l
	}

	boolean isRemoteFileOlder(File localFile, String remotePath) {
		def (long localTimestamp, long remoteTimestamp) = isUpToDate(localFile, remotePath)
		localTimestamp < remoteTimestamp
	}

	boolean isLocalFileOlder(File localFile, String remotePath) {
		def (long localTimestamp, long remoteTimestamp) = isUpToDate(localFile, remotePath)
		remoteTimestamp < localTimestamp
	}

	/**
	 * Checks to see if the remote file is current as compared with the local
	 * file. Returns true if the target file is up to date.
	 * @param ftp ftpclient
	 * @param localFile local file
	 * @param remoteFile remote file
	 * @return true if the target file is up to date
	 * @throws GradleException if the date of the remote files cannot be found and the action is
	 * GET_FILES
	 */
	private long[] isUpToDate(File localFile, String remoteFile) {
		log.debug("checking date for ${remoteFile}")

		if (timeDiffMillis == null) {
			throw new IllegalStateException("Application error: must call initialize() before invoking isUpToDate")
		}

		FTPFile[] files = ftp.listFiles(remoteFile)
		// For Microsoft's Ftp-Service an Array with length 0 is
		// returned if configured to return listings in "MS-DOS"-Format
		if (files == null || files.length == 0) {
			throw new GradleException("could not date test remote file: ${ftp.getReplyString()}")
		}

		long remoteTimestamp = files[0].getTimestamp().getTime().getTime()
		long localTimestamp = localFile.lastModified()
		long adjustedRemoteTimestamp = remoteTimestamp + timeDiffMillis + granularityMillis

		log.debug("   [${formatDate(localTimestamp)}] local")
		String message = "   [${formatDate(adjustedRemoteTimestamp)}] remote"
		if (remoteTimestamp != adjustedRemoteTimestamp) {
			message += " - (raw: ${formatDate(remoteTimestamp)})"
		}
		log.debug(message)
		[localTimestamp, adjustedRemoteTimestamp]
	}

	/**
	 * auto find the time difference between local and remote
	 * @return number of millis to add to remote time to make it comparable to local time
	 * @since ant 1.6
	 */
	private long calculateTimeDiff() {
		long returnValue = 0
		File tempFile = findTempFileName()
		try {
			long localTimeStamp = tempFile.lastModified()
			BufferedInputStream instream = new BufferedInputStream(new FileInputStream(tempFile))
			ftp.storeFile(tempFile.getName(), instream)
			instream.close()
			boolean success = FTPReply.isPositiveCompletion(ftp.getReplyCode())
			if (success) {
				FTPFile[] ftpFiles = ftp.listFiles(tempFile.getName())
				if (ftpFiles.length == 1) {
					long remoteTimeStamp = ftpFiles[0].getTimestamp().getTime().getTime()
					returnValue = localTimeStamp - remoteTimeStamp
				}
				ftp.deleteFile(ftpFiles[0].getName())
			}
			tempFile.delete()
		} catch (Exception e) {
			throw new GradleException("Failed to auto calculate time difference", e)
		}
		return returnValue
	}

	/**
	 *  find a suitable name for local and remote temporary file
	 */
	private File findTempFileName() {
		FTPFile[] theFiles = null
		final int maxIterations = 1000
		for (int counter = 1; counter < maxIterations; counter++) {
			File localFile = File.createTempFile("ant${Integer.toString(counter)}", "tmp")
			String fileName = localFile.getName()
			boolean found = false
			if (theFiles == null) {
				theFiles = ftp.listFiles()
			}
			for (int counter2 = 0; counter2 < theFiles.length; counter2++) {
				if (theFiles[counter2] != null
						&& theFiles[counter2].getName().equals(fileName)) {
					found = true
					break
				}
			}
			if (!found) {
				localFile.deleteOnExit()
				return localFile
			}
		}
		throw new GradleException("Failed to find suitable remote file within ${maxIterations} iterations")
	}

}

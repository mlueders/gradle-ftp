/*
 * Copyright 2014 Mike Lueders
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
	private static final SimpleDateFormat TIMESTAMP_LOGGING_SDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

	private static String formatDate(long timestamp) {
		synchronized (TIMESTAMP_LOGGING_SDF) {
			TIMESTAMP_LOGGING_SDF.format(new Date(timestamp))
		}
	}


	/**
	 * Automatically determine the time difference between local and remote machine, defaults to false.
	 *
	 * This requires right to create and delete a temporary file in the remote directory.
	 */
	boolean timeDiffAuto = false

	private FTPClient ftp
	/**
	 * Number of milliseconds to add to the time on the remote machine to get the time on the local machine.
	 */
	private long timeDiffMillis = 0l
	private boolean initialized = false

	public LastModifiedChecker(FTPClient ftp) {
		this.ftp = ftp
	}

	void initialize() {
		if (timeDiffAuto) {
			timeDiffMillis = calculateTimeDiff()
		}

		initialized = true
	}

	/**
	 * Checks to see if the remote file is current as compared with the local
	 * file. Returns true if the target file is up to date.
	 * @param localFile local file
	 * @param remoteFile remote file
	 * @param granularity amount to adjust remote timestamp.  Useful when server timestamps
	 * are HH:mm and client's are HH:mm:ss
	 * @see TimestampGranularity
	 */
	LastModifiedCheck getLastModifiedCheck(File localFile, String remoteFilePath, TimestampGranularity granularity) {
		log.debug("checking date for ${remoteFilePath}")

		if (!initialized) {
			throw new IllegalStateException("Application error: must call initialize() before invoking isUpToDate")
		}

		FTPFile remoteFile = getRemoteFile(remoteFilePath)
		// either there was a problem or there was no corresponding remote file
		if (remoteFile == null) {
			return
		}

		long remoteTimestamp = remoteFile.getTimestamp().getTime().getTime()
		long localTimestamp = localFile.lastModified()
		long adjustedRemoteTimestamp = remoteTimestamp + timeDiffMillis + granularity.getMilliseconds()

		log.debug("   [${formatDate(localTimestamp)}] local")
		String message = "   [${formatDate(adjustedRemoteTimestamp)}] remote"
		if (remoteTimestamp != adjustedRemoteTimestamp) {
			message += " - (raw: ${formatDate(remoteTimestamp)})"
		}
		log.debug(message)
		new LastModifiedCheck(localFile, remoteFilePath, localTimestamp, adjustedRemoteTimestamp)
	}

	private FTPFile getRemoteFile(String remoteFilePath) {
		FTPFile[] files = ftp.listFiles(remoteFilePath)

		// For Microsoft's Ftp-Service an Array with length 0 is
		// returned if configured to return listings in "MS-DOS"-Format
		if (files == null || files.length == 0) {
			return null
		}
		files[0]
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

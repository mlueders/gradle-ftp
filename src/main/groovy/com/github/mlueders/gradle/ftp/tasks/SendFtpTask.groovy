package com.github.mlueders.gradle.ftp.tasks

import com.github.mlueders.gradle.ftp.FtpAdapter
import com.github.mlueders.gradle.ftp.RetryHandler
import groovy.util.logging.Slf4j
import org.gradle.api.GradleException

@Slf4j
class SendFtpTask extends AbstractFtpTask {

	/**
	 * The file permission mode (Unix only) for files sent to the server.
	 */
	String chmod = null
	/**
	 * If true, transmit only files that are new or changed from their remote counterparts.
	 * Defaults to false, transmit all files.
	 * See the related attributes <code>timeDiffMillis</code> and <code>timeDiffAuto</code>.
	 * @see com.github.mlueders.gradle.ftp.LastModifiedChecker
	 */
	boolean newerOnly = false
	/**
	 * Used in conjunction with <code>newerOnly</code>
	 * @see TimestampGranularity
	 */
	TimestampGranularity timestampGranularity = TimestampGranularity.MINUTE

	@Override
	protected void onExecuteFtpTask(FtpFileProcessor processor, FtpAdapter ftpAdapter, RetryHandler retryHandler) {
		processor.remoteScanner = false
		processor.baseDirRequired = true
		processor.targetString = "files"
		processor.completedString = "sent"
		processor.actionString = "sending"

		if (newerOnly) {
			ftpAdapter.granularityMillis = timestampGranularity.getMilliseconds()
		}

		processor.transferFilesWithRetry { TransferableFile file ->
			sendFile(ftpAdapter, file.baseDir, file.relativePath)
		}
	}

	/**
	 * Sends a single file to the remote host. <code>filename</code> may
	 * contain a relative path specification. When this is the case, <code>sendFile</code>
	 * will attempt to create any necessary parent directories before sending
	 * the file. The file will then be sent using the entire relative path
	 * spec - no attempt is made to change directories. It is anticipated that
	 * this may eventually cause problems with some FTP servers, but it
	 * simplifies the coding.
	 * @param dir base directory of the file to be sent (local)
	 * @param filename relative path of the file to be send locally relative to dir
	 *        remotely relative to the remotedir attribute
	 */
	protected void sendFile(FtpAdapter ftpAdapter, String dir, String filename) {
		try {
			if (newerOnly && ftpAdapter.isRemoteFileOlder(dir, filename)) {
				return
			}
		} catch (GradleException ex) {
			log.debug("Could not date test remote file: ${filename} assuming out of date.")
		}

		boolean success = ftpAdapter.sendFile(dir, filename)
		// see if we should issue a chmod command
		if (success && (chmod != null)) {
			ftpAdapter.chmod(chmod, filename)
		}
	}

}

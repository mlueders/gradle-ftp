package com.github.mlueders.gradle.ftp.tasks

import com.github.mlueders.gradle.ftp.FtpAdapter
import com.github.mlueders.gradle.ftp.RetryHandler
import org.gradle.api.GradleException

class ChmodFtpTask extends AbstractFtpTask {

	/**
	 * The file permission mode (Unix only) for files sent to the server.
	 */
	String chmod = null

	protected void checkAttributes() throws GradleException {
		if (chmod == null) {
			throw new GradleException("chmod attribute must be set!")
		}
	}

	@Override
	protected void onExecuteFtpTask(FtpFileProcessor processor, FtpAdapter ftpAdapter, RetryHandler retryHandler) {
		processor.targetString = "files"
		processor.completedString = "mode changed"
		processor.actionString = "chmod"

		processor.transferFilesWithRetry { TransferableFile file ->
			ftpAdapter.chmod(chmod, file.relativePath)
		}
	}

}

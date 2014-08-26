package com.github.mlueders.gradle.ftp.tasks

import com.github.mlueders.gradle.ftp.FtpAdapter
import com.github.mlueders.gradle.ftp.RetryHandler
import org.gradle.api.GradleException

class ListFtpTask extends AbstractFtpTask {

	/**
	 * The output file for the "list" action. This attribute is ignored for any other actions.
	 */
    File listing

	protected void checkAttributes() throws GradleException {
		if (listing == null) {
			throw new GradleException("listing attribute must be set!")
		}
	}

	@Override
	protected void onExecuteFtpTask(FtpFileProcessor processor, FtpAdapter ftpAdapter, RetryHandler retryHandler) {
		processor.targetString = "files"
		processor.completedString = "listed"
		processor.actionString = "listing"

		processor.transferFilesWithRetry { TransferableFile file ->
			ftpAdapter.listFile(listing, file.relativePath)
		}
	}

}

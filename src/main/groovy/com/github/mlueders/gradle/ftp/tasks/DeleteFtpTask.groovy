package com.github.mlueders.gradle.ftp.tasks

import com.github.mlueders.gradle.ftp.FtpAdapter
import com.github.mlueders.gradle.ftp.RetryHandler

class DeleteFtpTask extends AbstractFtpTask {

	@Override
	protected void onExecuteFtpTask(FtpFileProcessor processor, FtpAdapter ftpAdapter, RetryHandler retryHandler) {
		processor.targetString = "files"
		processor.completedString = "deleted"
		processor.actionString = "deleting"

		processor.transferFilesWithRetry { TransferableFile file ->
			ftpAdapter.deleteFile(file.relativePath)
		}
	}

}

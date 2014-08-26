package com.github.mlueders.gradle.ftp.tasks

import com.github.mlueders.gradle.ftp.FtpAdapter
import com.github.mlueders.gradle.ftp.RetryHandler

class RmDirFtpTask extends AbstractFtpTask {

	@Override
	protected void onExecuteFtpTask(FtpFileProcessor processor, FtpAdapter ftpAdapter, RetryHandler retryHandler) {
		processor.targetString = "directories"
		processor.completedString = "removed"
		processor.actionString = "removing"

		// to remove directories, start by the end of the list
		// the trunk does not let itself be removed before the leaves
		List<TransferableFile> filesToTransfer = processor.getDirectoriesToTransfer().reverse()
		processor.transferFilesWithRetry(filesToTransfer) { TransferableFile file ->
			ftpAdapter.rmDir(file.relativePath)
		}
	}

}

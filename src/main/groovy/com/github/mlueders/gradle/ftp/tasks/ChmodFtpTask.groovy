package com.github.mlueders.gradle.ftp.tasks

import com.github.mlueders.gradle.ftp.FtpAdapter
import com.github.mlueders.gradle.ftp.RetryHandler
import groovy.io.FileType
import org.gradle.api.GradleException

class ChmodFtpTask extends AbstractFtpTask {

	/**
	 * The file permission mode (Unix only) for files sent to the server.
	 */
	String chmod = null
	/**
	 * The type of file to transfer
	 *
	 * Defaults to FileType.FILES
	 */
	FileType fileType = FileType.FILES

	protected void checkAttributes() throws GradleException {
		if (chmod == null) {
			throw new GradleException("chmod attribute must be set!")
		}

		if (fileType == null) {
			throw new GradleException("fileType must be set!")
		}
	}

	@Override
	protected void onExecuteFtpTask(FtpFileProcessor processor, FtpAdapter ftpAdapter, RetryHandler retryHandler) {
		processor.targetString = "files"
		processor.completedString = "mode changed"
		processor.actionString = "chmod"

		List<TransferableFile> filesToTransfer = getFilesToTransfer(processor)
		processor.transferFilesWithRetry(filesToTransfer) { TransferableFile file ->
			ftpAdapter.chmod(chmod, file.relativePath)
		}
	}

	private List<TransferableFile> getFilesToTransfer(FtpFileProcessor processor) {
		List<TransferableFile> filesToTransfer = []

		if (shouldMatchFilesOfType(FileType.FILES)) {
			filesToTransfer.addAll(processor.getFilesToTransfer())
		}

		if (shouldMatchFilesOfType(FileType.DIRECTORIES)) {
			filesToTransfer.addAll(processor.getDirectoriesToTransfer())
		}
		filesToTransfer
	}

	private boolean shouldMatchFilesOfType(FileType fileTypeToMatch) {
		(fileType == FileType.ANY) || (fileType == fileTypeToMatch)
	}

}

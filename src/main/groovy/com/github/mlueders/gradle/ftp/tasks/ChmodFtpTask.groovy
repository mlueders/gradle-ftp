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

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
import com.github.mlueders.gradle.ftp.LastModifiedCheck
import com.github.mlueders.gradle.ftp.RetryHandler
import com.github.mlueders.gradle.ftp.TimestampGranularity
import org.apache.commons.net.ftp.FTPFile
import org.gradle.api.GradleException

class GetFtpTask extends AbstractFtpTask {

	/**
	 * If true, transmit only files that are new or changed from their remote counterparts.
	 * Defaults to false, transmit all files.
	 * See the related attributes <code>timeDiffAuto</code> and <code>timeDiffAuto</code>.
	 * @see com.github.mlueders.gradle.ftp.LastModifiedChecker
	 */
	boolean newerOnly = false
	/**
	 * If true, modification times for "gotten" files will be preserved.
	 * Defaults to false.
	 */
	boolean preserveLastModified = false
	/**
	 * Used in conjunction with <code>newerOnly</code>
	 * @see com.github.mlueders.gradle.ftp.TimestampGranularity
	 */
	TimestampGranularity timestampGranularity = TimestampGranularity.NONE
	/**
	 * @see com.github.mlueders.gradle.ftp.LastModifiedChecker#timeDiffAuto
	 */
	Boolean timeDiffAuto

	@Override
	protected void checkAttributes() {
		if (newerOnly && (timestampGranularity == null)) {
			throw new GradleException("timestampGranularity must be non-null if newOnly is true")
		}
	}

	@Override
	protected void onExecuteFtpTask(FtpFileProcessor processor, FtpAdapter ftpAdapter, RetryHandler retryHandler) {
		processor.baseDirRequired = true
		processor.targetString = "files"
		processor.completedString = "retrieved"
		processor.actionString = "getting"

		if (timeDiffAuto != null) {
			ftpAdapter.setTimeDiffAuto(timeDiffAuto)
		}

		processor.transferFilesWithRetry { TransferableFile file ->
			getFile(ftpAdapter, file.baseDir, file.relativePath)
		}
	}

	/**
	 * Retrieve a single file from the remote host. <code>filename</code> may
	 * contain a relative path specification. <p>
	 *
	 * The file will then be retrieved using the entire relative path spec -
	 * no attempt is made to change directories. It is anticipated that this
	 * may eventually cause problems with some FTP servers, but it simplifies
	 * the coding.</p>
	 * @param dir local base directory to which the file should go back
	 * @param filename relative path of the file based upon the ftp remote directory
	 *        and/or the local base directory (dir)
	 * @throws org.gradle.api.GradleException if skipFailedTransfers is false
	 * and the file cannot be retrieved.
	 */
	protected void getFile(FtpAdapter ftpAdapter, String dir, String filename) throws GradleException {
		if (newerOnly) {
			LastModifiedCheck lastModifiedCheck = ftpAdapter.getLastModifiedCheck(dir, filename, timestampGranularity)

			if (lastModifiedCheck == null) {
				throw new GradleException("could not date test remote file: ${ftpAdapter.getFtpReplyString()}")
			} else if (lastModifiedCheck.isLocalFileOlder()) {
				return
			}
		}

		File transferredFile = ftpAdapter.getFile(dir, filename)
		if (preserveLastModified && (transferredFile != null)) {
			FTPFile[] remote = ftpAdapter.listFiles(filename)
			if (remote.length > 0) {
				transferredFile.setLastModified(remote[0].getTimestamp().getTime().getTime())
			}
		}
	}

}

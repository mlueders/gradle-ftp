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

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

class SiteCmdFtpTask extends AbstractFtpTask {

	/**
	 * Names the command that will be executed if the action is "site".
	 */
	String siteCommand

	protected void checkAttributes() throws GradleException {
		if (siteCommand == null) {
			throw new GradleException("sitecommand attribute must be set!")
		}
	}

	@Override
	protected void onExecuteFtpTask(FtpFileProcessor processor, FtpAdapter ftpAdapter, RetryHandler retryHandler) {
		retryHandler.execute("Site Command: ${siteCommand}") {
			ftpAdapter.doSiteCommand(siteCommand)
		}
	}

}

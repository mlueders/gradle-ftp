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

package com.github.mlueders.gradle.ftp.tasks

import com.github.mlueders.gradle.ftp.FtpAdapter
import com.github.mlueders.gradle.ftp.RetryHandler
import org.gradle.api.GradleException

class MkDirFtpTask extends AbstractFtpTask {

	protected void checkAttributes() throws GradleException {
		if (remoteDir == null) {
			throw new GradleException("remotedir attribute must be set!")
		}
	}

	@Override
	protected void onExecuteFtpTask(FtpFileProcessor processor, FtpAdapter ftpAdapter, RetryHandler retryHandler) {
		retryHandler.execute(remoteDir) {
			ftpAdapter.mkDir(remoteDir)
		}
	}

}

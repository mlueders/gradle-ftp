package com.github.mlueders.gradle.ftp

class LastModifiedCheck {

	private File localFile
	private String remotePath
	private long localLastModified
	private long remoteLastModified

	LastModifiedCheck(File localFile, String remotePath, long localLastModified, long remoteLastModified) {
		this.localFile = localFile
		this.remotePath = remotePath
		this.localLastModified = localLastModified
		this.remoteLastModified = remoteLastModified
	}

	boolean isRemoteFileOlder() {
		localLastModified < remoteLastModified
	}

	boolean isLocalFileOlder() {
		remoteLastModified < localLastModified
	}

}

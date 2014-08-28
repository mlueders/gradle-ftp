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

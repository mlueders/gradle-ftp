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

/**
 * Represents one of the valid timestamp adjustment values
 *
 * A timestamp adjustment may be used in file transfers for checking
 * uptodateness. MINUTE means to add one minute to the server
 * timestamp.  This is done because FTP servers typically list
 * timestamps HH:mm and client FileSystems typically use HH:mm:ss.
 *
 * The default is to use MINUTE for PUT actions and NONE for GET
 * actions, since GETs have the <code>preserveLastModified</code>
 * option, which takes care of the problem in most use cases where
 * this level of granularity is an issue.
 */
public enum TimestampGranularity {
	NONE(0L),
	MINUTE(60000L)

	private milliseconds

	private TimestampGranularity(long milliseconds) {
		this.milliseconds = milliseconds
	}

	public long getMilliseconds() {
		milliseconds
	}

}

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

import org.mockftpserver.fake.filesystem.FileSystemEntry

class MkDirFtpTaskSpec extends AbstractFtpTaskSpec<MkDirFtpTask> {

	@Override
	protected Class<AbstractFtpTask> getTaskType() {
		return MkDirFtpTask
	}

	def "should create directory"() {
		given:
		ftpTask.remoteDir = 'new-dir'

		when:
		ftpTask.executeFtpTask()

		then:
		assert ftpFileSystem.getEntry(ftpBaseDir.file('new-dir').path).isDirectory()
	}

	def "should create subdirectory"() {
		given:
		ftpTask.remoteDir = 'new-dir'
		ftpTask.executeFtpTask()

		when:
		ftpTask.remoteDir = 'new-dir/sub-dir'
		ftpTask.executeFtpTask()

		then:
		assert ftpFileSystem.getEntry(ftpBaseDir.file('new-dir/sub-dir').path).isDirectory()
	}

	def "should create directory hierarchy"() {
		given:
		ftpTask.remoteDir = 'new-dir/new-sub-dir'

		when:
		ftpTask.executeFtpTask()

		then:
		FileSystemEntry entry = ftpFileSystem.getEntry(ftpBaseDir.file('new-dir').file('new-sub-dir').path)
		assert entry.isDirectory()
	}

}

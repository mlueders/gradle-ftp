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

import com.bancvue.gradle.test.TestFile
import org.mockftpserver.fake.filesystem.DirectoryEntry

class RmDirFtpTaskSpec extends AbstractFtpTaskSpec<RmDirFtpTask> {

	String someDirPath

	@Override
	protected Class<RmDirFtpTask> getTaskType() {
		RmDirFtpTask
	}

	def setup() {
		someDirPath = ftpBaseDir.file('some-dir').absolutePath
		addFtpDir(someDirPath)
		assert ftpFileSystem.getEntry(someDirPath).isDirectory()
	}

	def "should remove remote directory"() {
		given:
		ftpTask.fileset {
			include(name: 'some-dir')
		}

		when:
		ftpTask.executeFtpTask()

		then:
		assert !ftpFileSystem.getEntry(someDirPath)
	}

	def "should remove remote sub-directory"() {
		given:
		String someSubDirPath = ftpBaseDir.file('some-dir').file('some-sub-dir').absolutePath
		addFtpDir(someSubDirPath)
		ftpTask.fileset {
			include(name: 'some-dir/some-sub-dir')
		}

		when:
		ftpTask.executeFtpTask()

		then:
		assert ftpFileSystem.getEntry(someDirPath)
		assert !ftpFileSystem.getEntry(someSubDirPath)
	}

	def "should remove hierarchy of remote directories"() {
		given:
		String someSubDirPath = ftpBaseDir.file('some-dir').file('some-sub-dir').absolutePath
		addFtpDir(someSubDirPath)
		ftpTask.fileset {
			include(name: 'some-dir/')
		}

		when:
		ftpTask.executeFtpTask()

		then:
		assert !ftpFileSystem.getEntry(someDirPath)
		assert !ftpFileSystem.getEntry(someSubDirPath)
	}

}

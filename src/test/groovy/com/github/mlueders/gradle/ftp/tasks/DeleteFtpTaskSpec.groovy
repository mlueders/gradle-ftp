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

class DeleteFtpTaskSpec extends AbstractFtpTaskSpec<DeleteFtpTask> {

	@Override
	protected Class<DeleteFtpTask> getTaskType() {
		DeleteFtpTask
	}

	def "should delete remote file"() {
		given:
		addFtpFile(ftpBaseDir.file('base-file'), 'base-file contents')
		assert ftpFileSystem.getEntry(ftpBaseDir.file('base-file').path)
		ftpTask.fileset {
			include(name: 'base-file')
		}

		when:
		ftpTask.executeFtpTask()

		then:
		assert !ftpFileSystem.getEntry(ftpBaseDir.file('base-file').path)
	}

	def "should not fail when deleting a file which does not exist"() {
		given:
		ftpTask.fileset {
			include(name: 'missing-file')
		}

		when:
		ftpTask.executeFtpTask()

		then:
		notThrown(Exception)
	}

}

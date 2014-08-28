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

import org.gradle.api.GradleException

class SiteCmdFtpTaskSpec extends AbstractFtpTaskSpec<SiteCmdFtpTask> {

	@Override
	protected Class getTaskType() {
		SiteCmdFtpTask
	}

	def "should send site command"() {
		given:
		ftpTask.siteCommand = "rm -rf star"

		when:
		ftpTask.executeFtpTask()

		then:
		getLoggedSiteCommands() == ["SITE rm -rf star"]
	}

	def "should throw exception if site command not set"() {
		when:
		ftpTask.executeFtpTask()

		then:
		thrown(GradleException)
	}

}

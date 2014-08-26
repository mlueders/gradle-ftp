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

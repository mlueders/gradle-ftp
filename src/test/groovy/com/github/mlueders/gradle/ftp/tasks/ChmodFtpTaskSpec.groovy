package com.github.mlueders.gradle.ftp.tasks

import org.gradle.api.GradleException

class ChmodFtpTaskSpec extends AbstractFtpTaskSpec<ChmodFtpTask> {

	@Override
	protected Class getTaskType() {
		ChmodFtpTask
	}

	def "should fail if chmod not set"() {
		when:
		ftpTask.executeFtpTask()

		then:
		thrown(GradleException)
	}

	def "should change mode of remote file using site command"() {
		given:
		addFtpFile(ftpBaseDir.file('base-file'))
		ftpTask.chmod = "644"
		ftpTask.fileset {
			include(name: 'base-file')
		}

		when:
		ftpTask.executeFtpTask()

		then:
		getLoggedSiteCommands() == ["SITE chmod 644 base-file"]
	}

	def "should change mode of all files in fileset"() {
		given:
		addFtpFile(ftpBaseDir.file('file1'))
		addFtpFile(ftpBaseDir.file('file2'))
		ftpTask.chmod = "644"
		ftpTask.fileset {
			include(name: 'file*')
		}

		when:
		ftpTask.executeFtpTask()

		then:
		getLoggedSiteCommands() == ["SITE chmod 644 file1", "SITE chmod 644 file2"]
	}

}

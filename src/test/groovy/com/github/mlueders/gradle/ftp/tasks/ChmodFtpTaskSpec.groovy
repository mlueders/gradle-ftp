package com.github.mlueders.gradle.ftp.tasks

import groovy.io.FileType
import org.gradle.api.GradleException
import spock.lang.Unroll

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

	@Unroll
	def "should change mode of #fileType when configured"() {
		given:
		addFtpFile(ftpBaseDir.file('dir/subdir/file'))
		ftpTask.fileType = fileType
		ftpTask.chmod = "755"
		ftpTask.fileset {
			include(name: 'dir/**')
		}

		when:
		ftpTask.executeFtpTask()

		then:
		getLoggedSiteCommands().sort() == expectedSiteCommands

		where:
		fileType             | expectedSiteCommands
		FileType.FILES       | ["SITE chmod 755 dir/subdir/file"]
		FileType.DIRECTORIES | ["SITE chmod 755 dir", "SITE chmod 755 dir/subdir"]
		FileType.ANY         | ["SITE chmod 755 dir", "SITE chmod 755 dir/subdir", "SITE chmod 755 dir/subdir/file"]
	}

}

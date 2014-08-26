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

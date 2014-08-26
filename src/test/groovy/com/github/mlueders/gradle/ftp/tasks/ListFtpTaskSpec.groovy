package com.github.mlueders.gradle.ftp.tasks

class ListFtpTaskSpec extends AbstractFtpTaskSpec<ListFtpTask> {

	@Override
	protected Class<ListFtpTask> getTaskType() {
		ListFtpTask
	}

	def 'list files'() {
		given:
		addFtpFile(ftpBaseDir.file('base-file'), 'base-file contents')
		ftpTask.listing = projectFS.file('listing')
		ftpTask.fileset {
			include(name: '**')
		}

		when:
		ftpTask.executeFtpTask()

		then:
		ftpTask.listing.text =~ /base-file/
	}

}

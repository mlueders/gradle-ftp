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

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

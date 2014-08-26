package com.github.mlueders.gradle.ftp.tasks

import com.bancvue.gradle.test.TestFile
import org.gradle.api.GradleException
import org.mockftpserver.fake.filesystem.FileEntry
import spock.lang.Unroll

class SendFtpTaskSpec extends AbstractFtpTaskSpec<SendFtpTask> {

	@Override
	protected Class<AbstractFtpTask> getTaskType() {
		SendFtpTask
	}

	def "should send files"() {
		given:
		ftpBaseDir.file('file-to-send') << 'file-to-send-content'
		ftpTask.fileset(ftpBaseDir.name) {
			include(name: '**')
		}

		when:
		ftpTask.executeFtpTask()

		then:
		FileEntry entry = ftpFileSystem.getEntry(ftpBaseDir.file('file-to-send').path)
		assert new String(entry.currentBytes) == 'file-to-send-content'
	}

	def "should fail if dir not set in fileset"() {
		given:
		ftpTask.fileset {
			include(name: '**')
		}

		when:
		ftpTask.executeFtpTask()

		then:
		thrown(GradleException)
	}

	@Unroll
	def "should #shouldOverwriteString remote file if newerOnly is #newerOnly and remote file is newer"() {
		given:
		TestFile targetDir = projectFS.mkdir('target-dir')
		TestFile localFile = targetDir.file('file.txt')
		localFile << 'out-of-date contents'
		/** @see #START_OF_DAY **/
		localFile.setLastModified(START_OF_DAY.minusSeconds(30).millis)
		FileEntry remoteFile = addFtpFile(ftpBaseDir.file('file.txt'), 'up-to-date contents')

		ftpTask.newerOnly = newerOnly
		ftpTask.fileset(targetDir.name) {
			include(name: 'file.txt')
		}

		when:
		ftpTask.executeFtpTask()

		then:
		assert new String(remoteFile.currentBytes) == expectedContent

		where:
		newerOnly | shouldOverwrite | shouldOverwriteString | expectedContent
		false     | true            | 'overwrite'           | 'out-of-date contents'
		true      | false           | 'not overwrite'       | 'up-to-date contents'
	}

	def "should change mode of sent file if chmod set"() {
		given:
		ftpTask.chmod = '644'
		ftpBaseDir.file('file-to-send') << 'file-to-send-content'
		ftpTask.fileset(ftpBaseDir.name) {
			include(name: '**')
		}

		when:
		ftpTask.executeFtpTask()

		then:
		assert ftpFileSystem.getEntry(ftpBaseDir.file('file-to-send').path)
		assert getLoggedSiteCommands() == ["SITE chmod 644 file-to-send"]
	}

}

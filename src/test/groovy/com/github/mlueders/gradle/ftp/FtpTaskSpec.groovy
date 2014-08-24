package com.github.mlueders.gradle.ftp

import com.bancvue.gradle.test.AbstractProjectSpecification
import com.bancvue.gradle.test.TestFile
import org.gradle.api.Project
import org.joda.time.DateTime
import org.joda.time.LocalDate
import org.mockftpserver.fake.FakeFtpServer
import org.mockftpserver.fake.UserAccount
import org.mockftpserver.fake.filesystem.DirectoryEntry
import org.mockftpserver.fake.filesystem.FileEntry
import org.mockftpserver.fake.filesystem.FileSystem
import org.mockftpserver.fake.filesystem.FileSystemEntry
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem
import spock.lang.Unroll

class FtpTaskSpec extends AbstractProjectSpecification {

	/**
	 * Okay, this is funky but I'm not sure what else to do.
	 * The timestamps received are always start of day.  Could be MockFtpServer is sending nothing
	 * and FTPClient is initializing to start of day, not sure.  In any case, this workaround should suffice.
	 * Not sure how it will function in other time zones but can cross that bridge if we come to it.
	 * The newerOnly tests will fail if this spec is initiated at the end of one day and the tests executed
	 * at the beginning of another, but that should be an exceedingly rare occurrence.
	 */
	private static final DateTime START_OF_DAY = new LocalDate().toDateTimeAtStartOfDay()

	private FtpTask ftpTask
	private TestFile ftpBaseDir
	private String ftpBaseDirPath
	private FileSystem ftpFileSystem

	def setup() {
		ftpBaseDir = projectFS.file('ftp')
		ftpBaseDirPath = ftpBaseDir.absolutePath

		ftpFileSystem = new UnixFakeFileSystem()
		ftpFileSystem.add(new DirectoryEntry(ftpBaseDir.absolutePath))
		addFtpFile(ftpBaseDir.file('base-file'), 'base-file contents')

		UserAccount userAccount = new UserAccount('user', 'password', ftpBaseDir.absolutePath)
		FakeFtpServer fakeFtpServer = new FakeFtpServer()
		fakeFtpServer.setServerControlPort(0)
		fakeFtpServer.addUserAccount(userAccount)
		fakeFtpServer.setFileSystem(ftpFileSystem)
		fakeFtpServer.start()

		ftpTask = project.tasks.create('ftpTask', FtpTask)
		ftpTask.configure {
			server = 'localhost'
			port = fakeFtpServer.getServerControlPort()
			userId = userAccount.getUsername()
			password = userAccount.getPassword()
		}
	}

	protected Project getProject() {
		super.project
	}

	private FileEntry addFtpFile(File file) {
		FileEntry entry = new FileEntry(file.absolutePath)
		ftpFileSystem.add(entry)
		entry
	}

	private FileEntry addFtpFile(File file, String contents) {
		FileEntry entry = new FileEntry(file.absolutePath, contents)
		ftpFileSystem.add(entry)
		entry
	}

	@Override
	String getProjectName() {
		return 'ftp'
	}

	def 'list files'() {
		given:
		ftpTask.action = FtpTask.Action.LIST_FILES
		ftpTask.listing = projectFS.file('listing')
		ftpTask.fileset {
			include(name: '**')
		}

		when:
		ftpTask.executeTask()

		then:
		ftpTask.listing.text =~ /base-file/
	}

	def 'get files'() {
		given:
		TestFile targetDir = projectFS.file('target-dir')
		targetDir.mkdirs()
		ftpTask.action = FtpTask.Action.GET_FILES
		ftpTask.fileset(targetDir.name) {
			include(name: '**')
		}

		when:
		ftpTask.executeTask()

		then:
		TestFile baseFile = targetDir.file('base-file')
		assert baseFile.exists()
		assert baseFile.text == 'base-file contents'
	}

	@Unroll
	def "should set lastModified of local file to #description if preserveLastModified is #preserveLastModified"() {
		given:
		TestFile targetDir = projectFS.file('target-dir')
		targetDir.mkdirs()

		ftpTask.preserveLastModified = preserveLastModified
		ftpTask.action = FtpTask.Action.GET_FILES
		ftpTask.fileset(targetDir.name) {
			include(name: '**')
		}

		when:
		ftpTask.executeTask()

		then:
		TestFile baseFile = targetDir.file('base-file')
		/** @see #START_OF_DAY **/
		if (preserveLastModified) {
			assert baseFile.lastModified() == START_OF_DAY.millis
		} else {
			assert new DateTime().minusSeconds(5).isBefore(baseFile.lastModified())
		}

		where:
		preserveLastModified | description
		true                 | "lastModified time of remote file"
		false                | "current local time"
	}

	def 'send files'() {
		given:
		ftpBaseDir.file('file-to-send') << 'file-to-send-content'
		ftpTask.action = FtpTask.Action.SEND_FILES
		ftpTask.fileset(ftpBaseDir.name) {
			include(name: '**')
		}

		when:
		ftpTask.executeTask()

		then:
		FileEntry entry = ftpFileSystem.getEntry(ftpBaseDir.file('file-to-send').path)
		assert new String(entry.currentBytes) == 'file-to-send-content'
	}

	def 'delete files'() {
		given:
		assert ftpFileSystem.getEntry(ftpBaseDir.file('base-file').path)
		ftpTask.action = FtpTask.Action.DEL_FILES
		ftpTask.fileset {
			include(name: 'base-file')
		}

		when:
		ftpTask.executeTask()

		then:
		assert !ftpFileSystem.getEntry(ftpBaseDir.file('base-file').path)
	}

	def 'mkdir'() {
		given:
		ftpTask.action = FtpTask.Action.MK_DIR
		ftpTask.remoteDir = 'new-dir'

		when:
		ftpTask.executeTask()

		then:
		FileSystemEntry entry = ftpFileSystem.getEntry(ftpBaseDir.file('new-dir').path)
		assert entry.isDirectory()
	}

	def "mkdir should construct hierarchy"() {
		given:
		ftpTask.action = FtpTask.Action.MK_DIR
		ftpTask.remoteDir = 'new-dir/new-sub-dir'

		when:
		ftpTask.executeTask()

		then:
		FileSystemEntry entry = ftpFileSystem.getEntry(ftpBaseDir.file('new-dir').file('new-sub-dir').path)
		assert entry.isDirectory()
	}

	def 'rmdir'() {
		given:
		ftpFileSystem.add(new DirectoryEntry(ftpBaseDir.file('some-dir').absolutePath))
		ftpTask.action = FtpTask.Action.RM_DIR
		ftpTask.fileset {
			include(name: 'some-dir')
		}

		when:
		ftpTask.executeTask()

		then:
		assert !ftpFileSystem.getEntry(ftpBaseDir.file('some-dir').absolutePath)
	}

	@Unroll
	def "should #shouldOverwriteString local file if newerOnly is #newerOnly and local file is newer"() {
		given:
		TestFile targetDir = projectFS.file('target-dir')
		targetDir.mkdirs()
		TestFile localFile = targetDir.file('file.txt')
		localFile << 'up-to-date contents'
		/** @see #START_OF_DAY **/
		localFile.setLastModified(START_OF_DAY.plusSeconds(30).millis)
		addFtpFile(ftpBaseDir.file('file.txt'), 'out-of-date contents')

		ftpTask.newerOnly = newerOnly
		ftpTask.action = FtpTask.Action.GET_FILES
		ftpTask.fileset(targetDir.name) {
			include(name: 'file.txt')
		}

		when:
		ftpTask.executeTask()

		then:
		assert localFile.text == expectedContent

		where:
		newerOnly | shouldOverwrite | shouldOverwriteString | expectedContent
		false     | true            | 'overwrite'           | 'out-of-date contents'
		true      | false           | 'not overwrite'       | 'up-to-date contents'
	}

	@Unroll
	def "should #shouldOverwriteString remote file if newerOnly is #newerOnly and remote file is newer"() {
		given:
		TestFile targetDir = projectFS.file('target-dir')
		targetDir.mkdirs()
		TestFile localFile = targetDir.file('file.txt')
		localFile << 'out-of-date contents'
		/** @see #START_OF_DAY **/
		localFile.setLastModified(START_OF_DAY.minusSeconds(30).millis)
		FileEntry remoteFile = addFtpFile(ftpBaseDir.file('file.txt'), 'up-to-date contents')

		ftpTask.newerOnly = newerOnly
		ftpTask.action = FtpTask.Action.SEND_FILES
		ftpTask.fileset(targetDir.name) {
			include(name: 'file.txt')
		}

		when:
		ftpTask.executeTask()

		then:
		assert new String(remoteFile.currentBytes) == expectedContent

		where:
		newerOnly | shouldOverwrite | shouldOverwriteString | expectedContent
		false     | true            | 'overwrite'           | 'out-of-date contents'
		true      | false           | 'not overwrite'       | 'up-to-date contents'
	}

}
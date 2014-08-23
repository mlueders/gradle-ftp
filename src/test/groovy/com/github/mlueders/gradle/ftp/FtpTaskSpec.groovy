package com.github.mlueders.gradle.ftp

import com.bancvue.gradle.test.AbstractProjectSpecification
import com.bancvue.gradle.test.TestFile
import org.gradle.api.Project
import org.mockftpserver.fake.FakeFtpServer
import org.mockftpserver.fake.UserAccount
import org.mockftpserver.fake.filesystem.DirectoryEntry
import org.mockftpserver.fake.filesystem.FileEntry
import org.mockftpserver.fake.filesystem.FileSystem
import org.mockftpserver.fake.filesystem.FileSystemEntry
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem

class FtpTaskSpec extends AbstractProjectSpecification {

	private FtpTask ftpTask
	private TestFile ftpBaseDir
	private String ftpBaseDirPath
	private FileSystem ftpFileSystem

	def setup() {
		ftpBaseDir = projectFS.file('ftp')
		ftpBaseDirPath = ftpBaseDir.absolutePath

		ftpFileSystem = new UnixFakeFileSystem()
		ftpFileSystem.add(new DirectoryEntry(ftpBaseDir.absolutePath))
		ftpFileSystem.add(new FileEntry(ftpBaseDir.file('base-file').absolutePath, 'base-file contents'))

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
		println 'FILES: ' + targetDir.listFiles()
		assert baseFile
		assert baseFile.text == 'base-file contents'
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

}

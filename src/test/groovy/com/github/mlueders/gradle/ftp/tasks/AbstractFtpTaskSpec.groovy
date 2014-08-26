package com.github.mlueders.gradle.ftp.tasks

import com.bancvue.gradle.test.AbstractProjectSpecification
import com.bancvue.gradle.test.TestFile
import org.gradle.api.Project
import org.joda.time.DateTime
import org.joda.time.LocalDate
import org.mockftpserver.core.command.Command
import org.mockftpserver.core.command.CommandNames
import org.mockftpserver.core.session.Session
import org.mockftpserver.fake.FakeFtpServer
import org.mockftpserver.fake.UserAccount
import org.mockftpserver.fake.command.SiteCommandHandler
import org.mockftpserver.fake.filesystem.DirectoryEntry
import org.mockftpserver.fake.filesystem.FileEntry
import org.mockftpserver.fake.filesystem.FileSystem
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem

abstract class AbstractFtpTaskSpec<T extends AbstractFtpTask> extends AbstractProjectSpecification {

	private static class SiteCommandLogger extends SiteCommandHandler {

		private List<String> commands = []

		protected void handle(Command command, Session session) {
			super.handle(command, session)
			commands.add("${command.name} ${command.parameters.join(" ")}")
		}

	}

	/**
	 * Okay, this is funky but I'm not sure what else to do.
	 * The timestamps received are always start of day.  Could be MockFtpServer is sending nothing
	 * and FTPClient is initializing to start of day, not sure.  In any case, this workaround should suffice.
	 * Not sure how it will function in other time zones but can cross that bridge if we come to it.
	 * The newerOnly tests will fail if this spec is initiated at the end of one day and the tests executed
	 * at the beginning of another, but that should be an exceedingly rare occurrence.
	 */
	static final DateTime START_OF_DAY = new LocalDate().toDateTimeAtStartOfDay()

	protected T ftpTask
	protected TestFile ftpBaseDir
	protected String ftpBaseDirPath
	protected FileSystem ftpFileSystem
	protected SiteCommandLogger siteCommandLogger

	@Override
	String getProjectName() {
		"ftp"
	}

	def setup() {
		ftpBaseDir = projectFS.mkdir('ftp')
		ftpBaseDirPath = ftpBaseDir.absolutePath

		siteCommandLogger = new SiteCommandLogger()

		ftpFileSystem = new UnixFakeFileSystem()
		ftpFileSystem.add(new DirectoryEntry(ftpBaseDir.absolutePath))

		UserAccount userAccount = new UserAccount('user', 'password', ftpBaseDir.absolutePath)
		FakeFtpServer fakeFtpServer = new FakeFtpServer()
		fakeFtpServer.setServerControlPort(0)
		fakeFtpServer.addUserAccount(userAccount)
		fakeFtpServer.setFileSystem(ftpFileSystem)
		fakeFtpServer.setCommandHandler(CommandNames.SITE, siteCommandLogger)
		fakeFtpServer.start()

		ftpTask = project.tasks.create('ftpTask', getTaskType())
		ftpTask.configure {
			server = 'localhost'
			port = fakeFtpServer.getServerControlPort()
			userId = userAccount.getUsername()
			password = userAccount.getPassword()
		}
	}

	protected List<String> getLoggedSiteCommands() {
		siteCommandLogger.commands
	}

	protected abstract Class<T> getTaskType()

	protected Project getProject() {
		super.project
	}

	protected FileEntry addFtpFile(File file) {
		FileEntry entry = new FileEntry(file.absolutePath)
		ftpFileSystem.add(entry)
		entry
	}

	protected FileEntry addFtpFile(File file, String contents) {
		FileEntry entry = new FileEntry(file.absolutePath, contents)
		ftpFileSystem.add(entry)
		entry
	}

	protected DirectoryEntry addFtpDir(File file) {
		addFtpDir(file.absolutePath)
	}

	protected DirectoryEntry addFtpDir(String path) {
		DirectoryEntry entry = new DirectoryEntry(path)
		ftpFileSystem.add(entry)
		entry
	}

}

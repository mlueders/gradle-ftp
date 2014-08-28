/*
 * Copyright 2014 Mike Lueders
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.mlueders.gradle.ftp

import com.bancvue.gradle.test.AbstractProjectSpecification
import com.bancvue.gradle.test.TestFile
import org.mockftpserver.fake.FakeFtpServer
import org.mockftpserver.fake.UserAccount
import org.mockftpserver.fake.filesystem.DirectoryEntry
import org.mockftpserver.fake.filesystem.FileEntry
import org.mockftpserver.fake.filesystem.FileSystem
import org.mockftpserver.fake.filesystem.UnixFakeFileSystem

class FtpDirectoryScannerSpec extends AbstractProjectSpecification {

	private FtpAdapter ftpAdapter
	private TestFile ftpBaseDir
	private String ftpBaseDirPath
	private FileSystem ftpFileSystem
	private FtpDirectoryScanner scanner

	@Override
	String getProjectName() {
		"ftp"
	}

	def setup() {
		ftpBaseDir = projectFS.file('ftp')
		ftpBaseDir.mkdirs()
		ftpBaseDirPath = ftpBaseDir.absolutePath

		ftpFileSystem = new UnixFakeFileSystem()
		ftpFileSystem.add(new DirectoryEntry(ftpBaseDir.absolutePath))
		addFtpFile(ftpBaseDir.file('rootFile'))
		addFtpFile(ftpBaseDir.file('rootDir').file('subFile'))

		UserAccount userAccount = new UserAccount('user', 'password', ftpBaseDir.absolutePath)
		FakeFtpServer fakeFtpServer = new FakeFtpServer()
		fakeFtpServer.setServerControlPort(0)
		fakeFtpServer.addUserAccount(userAccount)
		fakeFtpServer.setFileSystem(ftpFileSystem)
		fakeFtpServer.start()

		ftpAdapter = new FtpAdapter(new FtpAdapter.Config())
		ftpAdapter.server = 'localhost'
		ftpAdapter.port = fakeFtpServer.getServerControlPort()
		ftpAdapter.userId = userAccount.getUsername()
		ftpAdapter.password = userAccount.getPassword()
		ftpAdapter.open()
	}

	def cleanup() {
		ftpAdapter?.close()
	}

	private void addFtpFile(File file) {
		ftpFileSystem.add(new FileEntry(file.absolutePath))
	}

	private void assertScanResults(List includedDirectories, List includedFiles) {
		assert scanner.getIncludedDirectories().sort() == includedDirectories
		assert scanner.getIncludedFiles().sort() == includedFiles
	}

	def "should scan remote root if remoteDir is null"() {
		given:
		scanner = new FtpDirectoryScanner(ftpAdapter.ftp, null, "/")

		when:
		scanner.scan()

		then:
		assertScanResults(['rootDir'], ['rootDir/subFile', 'rootFile'])
	}

	def "should scan remote directory and return files relative to that directory"() {
		given:
		scanner = new FtpDirectoryScanner(ftpAdapter.ftp, 'rootDir', "/")
		addFtpFile(ftpBaseDir.file('rootDir').file('subDir').file('subSubFile'))

		when:
		scanner.scan()

		then:
		assertScanResults(['subDir'], ['subDir/subSubFile', 'subFile'])
	}

	def "should return empty list if remote dir does not exist"() {
		given:
		scanner = new FtpDirectoryScanner(ftpAdapter.ftp, 'missingDir', "/")

		when:
		scanner.scan()

		then:
		assertScanResults([], [])
	}

	def "should match file with include"() {
		given:
		scanner = new FtpDirectoryScanner(ftpAdapter.ftp, null, "/")

		when:
		scanner.includes = ['**/subFile']
		scanner.scan()

		then:
		assertScanResults([], ['rootDir/subFile'])
	}

	def "should match file case sensitive by default"() {
		given:
		scanner = new FtpDirectoryScanner(ftpAdapter.ftp, null, "/")

		when:
		scanner.includes = ['**/SUBFILE']
		scanner.scan()

		then:
		assertScanResults([], [])
	}

	def "should match file case insensitive if configured"() {
		given:
		scanner = new FtpDirectoryScanner(ftpAdapter.ftp, null, "/")
		scanner.caseSensitive = false

		when:
		scanner.includes = ['**/SUBFILE']
		scanner.scan()

		then:
		assertScanResults([], ['rootDir/subFile'])
	}

	def "should match dir with include"() {
		given:
		scanner = new FtpDirectoryScanner(ftpAdapter.ftp, null, "/")

		when:
		scanner.includes = ['rootDir/']
		scanner.scan()

		then:
		assertScanResults(['rootDir'], ['rootDir/subFile'])
	}

	def "should not match files with directory if include path adoes not end with separator"() {
		given:
		scanner = new FtpDirectoryScanner(ftpAdapter.ftp, null, "/")

		when:
		scanner.includes = ['rootDir']
		scanner.scan()

		then:
		assertScanResults(['rootDir'], [])
	}

	def "should match dir case sensitive by default"() {
		given:
		scanner = new FtpDirectoryScanner(ftpAdapter.ftp, null, "/")

		when:
		scanner.includes = ['ROOTDIR/']
		scanner.scan()

		then:
		assertScanResults([], [])
	}

	def "should match dir case insensitive if configured"() {
		given:
		scanner = new FtpDirectoryScanner(ftpAdapter.ftp, null, "/")

		when:
		scanner.includes = ['ROOTDIR/']
		scanner.caseSensitive = false
		scanner.scan()

		then:
		assertScanResults(['rootDir'], ['rootDir/subFile'])
	}

	def "should exclude individual file"() {
		given:
		scanner = new FtpDirectoryScanner(ftpAdapter.ftp, null, "/")
		addFtpFile(ftpBaseDir.file('rootDir').file('otherFile'))

		when:
		scanner.includes = ['rootDir/']
		scanner.excludes = ['**/otherFile']
		scanner.scan()

		then:
		assertScanResults(['rootDir'], ['rootDir/subFile'])
	}

	def "exclude should have precedence over include"() {
		given:
		scanner = new FtpDirectoryScanner(ftpAdapter.ftp, null, "/")
		addFtpFile(ftpBaseDir.file('rootDir').file('otherFile'))

		when:
		scanner.includes = ['rootDir/']
		scanner.excludes = ['rootDir/']
		scanner.scan()

		then:
		assertScanResults([], [])
	}

}

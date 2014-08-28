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

import groovy.util.logging.Slf4j
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPClientConfig
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import org.apache.tools.ant.DirectoryScanner
import org.apache.tools.ant.util.FileUtils
import org.gradle.api.GradleException

@Slf4j
class FtpAdapter {

	static class Config {
		/**
		 * Advanced configuration
		 * @see org.apache.commons.net.ftp.FTPClientConfig
		 */
		FTPClientConfig clientConfig
		/**
		 * The FTP server to send files to.
		 */
		String server
		/**
		 * the FTP port used by the remote server.
		 */
		int port = DEFAULT_FTP_PORT
		/**
		 * The login user id to use on the specified server.
		 */
		String userId
		/**
		 * The login password for the given user id.
		 */
		String password
		/**
		 * The login account to use on the specified server.
		 */
		String account
		/**
		 * If true, verifies that data and control connections are connected to the same remote host.
		 * Defaults to true
		 */
		boolean enableRemoteVerification = true
		/**
		 * If true, uses binary mode, otherwise text mode.
		 * Defaults to true.
		 */
		boolean binary = true
		/**
		 * Specifies whether to use passive mode. Set to true if you are behind a
		 * firewall and cannot connect without it. Passive mode is disabled by default.
		 */
		boolean passive = false
		/**
		 * Set to true to receive notification about each file as it is
		 * transferred.
		 */
		boolean verbose = false
		/**
		 * If true, skip errors on directory creation.
		 * Defaults to false.
		 */
		boolean ignoreNoncriticalErrors = false
		/**
		 * If true, unsuccessful file put, delete and get operations to be skipped with a warning
		 * and the remainder of the files still transferred.
		 * Defaults to false.
		 */
		boolean skipFailedTransfers = false
		/**
		 * Names a site command that will be executed immediately after connection.
		 */
		String initialSiteCommand
		/**
		 * The default mask for file creation on a unix server.
		 */
		String umask = null
		/**
		 * The remote file separator character. This normally defaults to the Unix standard forward slash,
		 * but can be manually overridden if the remote server requires some other separator.
		 * Only the first character of the string is used.
		 */
		String remoteFileSep = "/"
	}


	/** Default port for FTP */
	private static final int DEFAULT_FTP_PORT = 21

	private static final String LINE_SEPARATOR = System.getProperty("file.separator")

	/**
	 * Codes 521, 550 and 553 can be produced by FTP Servers to indicate that an attempt
	 * to create a directory has failed because the directory already exists.
	 */
	private static List<Integer> DIRECTORY_ALREADY_EXISTS_RETURN_CODES = [521, 550, 553]

	@Delegate
	private Config config
	private FTPClient ftp
	private LastModifiedChecker lastModifiedChecker
	private Set dirCache = new HashSet()
	private int transferred = 0
	private int skipped = 0

	public FtpAdapter(Config config) {
		this.config = config
		this.ftp = new FTPClient()
		this.lastModifiedChecker = new LastModifiedChecker(ftp)
	}

	public int getTransferred() {
		transferred
	}

	public int getSkipped() {
		skipped
	}

	void open(RetryHandler retryHandler) {
		checkAttributes()
		skipped = 0
		transferred = 0

		if (clientConfig) {
			ftp.configure(clientConfig)
		}

		lastModifiedChecker.initialize()

		log.debug("opening FTP connection to ${server}")
		ftp.setRemoteVerificationEnabled(enableRemoteVerification)
		ftp.connect(server, port)
		if (!isPositiveCompletion()) {
			throw new GradleException("FTP connection failed: ${ftp.getReplyString()}")
		}

		log.debug("connected, logging in to FTP server")
		if ((this.account != null && !ftp.login(userId, password, account))
				|| (this.account == null && !ftp.login(userId, password))) {
			throw new GradleException("Could not login to FTP server")
		}

		log.debug("login succeeded")
		int fileType = binary ? FTP.BINARY_FILE_TYPE : FTP.ASCII_FILE_TYPE
		ftp.setFileType(fileType)
		if (!isPositiveCompletion()) {
			throw new GradleException("could not set transfer type: ${ftp.getReplyString()}")
		}

		if (passive) {
			log.debug("entering passive mode")
			ftp.enterLocalPassiveMode()
			if (!isPositiveCompletion()) {
				throw new GradleException("could not enter into passive mode: ${ftp.getReplyString()}")
			}
		}

		// If an initial command was configured then send it.
		// Some FTP servers offer different modes of operation,
		// E.G. switching between a UNIX file system mode and a legacy file system.
		if (this.initialSiteCommand != null) {
			retryHandler.execute("initial site command: ${initialSiteCommand}", log) {
				doSiteCommand(initialSiteCommand)
			}
		}

		// For a unix ftp server you can set the default mask for all files created.
		if (umask != null) {
			retryHandler.execute("umask ${umask}", log) {
				doSiteCommand("umask ${umask}")
			}
		}
	}

	private void checkAttributes() throws GradleException {
		if (server == null) {
			throw new GradleException("server attribute must be set!")
		}
		if (userId == null) {
			throw new GradleException("userId attribute must be set!")
		}
		if (password == null) {
			throw new GradleException("password attribute must be set!")
		}
	}

	void close() {
		if (ftp.isConnected()) {
			try {
				log.debug("disconnecting")
				ftp.logout()
				ftp.disconnect()
			} catch (IOException ex) {
				// ignore it
			}
		}
	}

	boolean isPositiveCompletion() {
		FTPReply.isPositiveCompletion(ftp.getReplyCode())
	}

	void chmod(String chmod, String filename) {
		String remoteFilePath = resolveRemotePath(filename)
		doSiteCommand("chmod ${chmod} ${remoteFilePath}")
		transferred++
	}

	/**
	 * Sends a site command to the ftp server
	 * @param theCMD command to execute
	 */
	void doSiteCommand(String theCMD) {
		log.debug("Doing Site Command: ${theCMD}")

		if (!ftp.sendSiteCommand(theCMD)) {
			log.warn("Failed to issue Site Command: ${theCMD}")
		} else {
			String[] myReply = ftp.getReplyStrings()

			for (int x = 0; x < myReply.length; x++) {
				if (myReply[x] != null && myReply[x].indexOf("200") == -1) {
					log.warn(myReply[x])
				}
			}
		}
	}

	/**
	 * Create the specified directory on the remote host.
	 *
	 * @param dir The directory to create (format must be correct for host
	 *      type)
	 * @throws GradleException if ignoreNoncriticalErrors has not been set to true
	 *         and a directory could not be created, for instance because it was
	 *         already existing. Precisely, the codes 521, 550 and 553 will trigger
	 *         a GradleException
	 */
	void mkDir(String dir) throws GradleException {
		String workingDirectory = ftp.printWorkingDirectory()
		if (verbose) {
			if (dir.startsWith("/") || workingDirectory == null) {
				log.info("Creating directory: ${dir} in /")
			} else {
				log.info("Creating directory: ${dir} in ${workingDirectory}")
			}
		}
		if (dir.startsWith("/")) {
			ftp.changeWorkingDirectory("/")
		}
		String subdir
		StringTokenizer st = new StringTokenizer(dir, "/")
		while (st.hasMoreTokens()) {
			subdir = st.nextToken()
			log.debug("Checking ${subdir}")
			if (!ftp.changeWorkingDirectory(subdir)) {
				if (!ftp.makeDirectory(subdir)) {
					handleMkDirFailure()
					if (verbose) {
						log.info("Directory already exists")
					}
				} else {
					if (verbose) {
						log.info("Directory created OK")
					}
					ftp.changeWorkingDirectory(subdir)
				}
			}
		}
		if (workingDirectory != null) {
			ftp.changeWorkingDirectory(workingDirectory)
		}
	}

	/**
	 * look at the response for a failed mkdir action, decide whether
	 * it matters or not. If it does, we throw an exception
	 * @throws GradleException if this is an error to signal
	 */
	private void handleMkDirFailure() throws GradleException {
		int rc = ftp.getReplyCode()
		if (!(ignoreNoncriticalErrors && DIRECTORY_ALREADY_EXISTS_RETURN_CODES.contains(rc))) {
			throw new GradleException("could not create directory: ${ftp.getReplyString()}")
		}
	}

	void changeWorkingDirectory(String workingDir) {
		log.debug("changing the remote directory to ${workingDir}")
		if (!ftp.changeWorkingDirectory(workingDir)) {
			throw new GradleException("could not change remote directory: ${ftp.getReplyString()}")
		}
	}

	/**
	 * Delete a directory, if empty, from the remote host.
	 * @param dirpath directory to delete
	 * @throws GradleException if skipFailedTransfers is set to false and the deletion could not be done
	 */
	void rmDir(String dirpath) throws GradleException {
		if (verbose) {
			log.info("removing ${dirpath}")
		}

		if (!ftp.removeDirectory(resolveRemotePath(dirpath))) {
			String s = "could not remove directory: ${ftp.getReplyString()}"

			if (skipFailedTransfers) {
				skipped++
				log.warn(s)
			} else {
				throw new GradleException(s)
			}
		} else {
			log.debug("Directory ${dirpath} removed from ${server}")
			transferred++
		}
	}

	/**
	 * Correct a file path to correspond to the remote host requirements. This
	 * implementation currently assumes that the remote end can handle
	 * Unix-style paths with forward-slash separators. This can be overridden
	 * with the <code>separator</code> task parameter. No attempt is made to
	 * determine what syntax is appropriate for the remote host.
	 *
	 * @param file the remote file name to be resolved
	 *
	 * @return the filename as it will appear on the server.
	 */
	private String resolveRemotePath(String file) {
		return file.replace(LINE_SEPARATOR, remoteFileSep)
	}

	/**
	 * List information about a single file from the remote host. <code>filename</code>
	 * may contain a relative path specification. <p>
	 *
	 * The file listing will then be retrieved using the entire relative path
	 * spec - no attempt is made to change directories. It is anticipated that
	 * this may eventually cause problems with some FTP servers, but it
	 * simplifies the coding.</p>
	 * @param bw buffered writer
	 * @param filename the directory one wants to list
	 */
	void listFile(File listingFile, String filename) {
		listingFile.getParentFile().mkdirs()

		if (verbose) {
			log.info("listing ${filename}")
		}

		FTPFile[] ftpfiles = listFiles(filename)
		if (ftpfiles != null && ftpfiles.length > 0) {
			listingFile << "${ftpfiles[0].toString()}${LINE_SEPARATOR}"
			transferred++
		}
	}

	FTPFile[] listFiles(String filename) {
		String remotePath = resolveRemotePath(filename)
		ftp.listFiles(remotePath)
	}

	/**
	 * Delete a file from the remote host.
	 * @param filename file to delete
	 * @throws GradleException if skipFailedTransfers is set to false
	 * and the deletion could not be done
	 */
	void deleteFile(String filename) throws GradleException {
		if (verbose) {
			log.info("deleting ${filename}")
		}

		if (!ftp.deleteFile(resolveRemotePath(filename))) {
			String s = "could not delete file: " + ftp.getReplyString()

			if (skipFailedTransfers) {
				log.warn(s)
				skipped++
			} else {
				throw new GradleException(s)
			}
		} else {
			log.debug("File ${filename} deleted from ${server}")
			transferred++
		}
	}

	/**
	 * Retrieve a single file from the remote host. <code>filename</code> may
	 * contain a relative path specification. <p>
	 *
	 * The file will then be retrieved using the entire relative path spec -
	 * no attempt is made to change directories. It is anticipated that this
	 * may eventually cause problems with some FTP servers, but it simplifies
	 * the coding.</p>
	 * @param dir local base directory to which the file should go back
	 * @param filename relative path of the file based upon the ftp remote directory
	 *        and/or the local base directory (dir)
	 * @return the transferred File on successful transfer, null otherwise
	 * @throws GradleException if skipFailedTransfers is false and the file cannot be retrieved.
	 */
	File getFile(String dir, String filename) throws GradleException {
		String remoteFilePath = resolveRemotePath(filename)
		File transferredFile = null

		OutputStream outstream = null
		try {
			File file = new File(dir, filename)

			if (verbose) {
				log.info("transferring ${filename} to ${file.getAbsolutePath()}")
			}

			file.parentFile.mkdirs()

			outstream = new BufferedOutputStream(new FileOutputStream(file))
			ftp.retrieveFile(remoteFilePath, outstream)

			if (!isPositiveCompletion()) {
				String s = "could not get file: " + ftp.getReplyString()

				if (skipFailedTransfers) {
					log.warn(s)
					skipped++
				} else {
					throw new GradleException(s)
				}

			} else {
				log.debug("File ${file.getAbsolutePath()} copied from ${server}")
				transferredFile = file
				transferred++
			}
		} finally {
			FileUtils.close(outstream)
		}
		transferredFile
	}

	/**
	 * Sends a single file to the remote host. <code>filename</code> may
	 * contain a relative path specification. When this is the case, <code>sendFile</code>
	 * will attempt to create any necessary parent directories before sending
	 * the file. The file will then be sent using the entire relative path
	 * spec - no attempt is made to change directories. It is anticipated that
	 * this may eventually cause problems with some FTP servers, but it
	 * simplifies the coding.
	 * @param dir base directory of the file to be sent (local)
	 * @param filename relative path of the file to be send
	 *        locally relative to dir
	 *        remotely relative to the remotedir attribute
	 */
	boolean sendFile(String dir, String filename) {
		File file = new File(dir, filename)
		String remoteFilePath = resolveRemotePath(filename)
		boolean success = false

		if (verbose) {
			log.info("transferring ${file.getAbsolutePath()}")
		}

		InputStream instream = null
		try {
			createParents(filename)

			instream = new BufferedInputStream(new FileInputStream(file))
			ftp.storeFile(remoteFilePath, instream)

			success = isPositiveCompletion()
			if (!success) {
				String s = "could not put file: " + ftp.getReplyString()

				if (skipFailedTransfers) {
					log.warn(s)
					skipped++
				} else {
					throw new GradleException(s)
				}

			} else {
				log.debug("File ${file.getAbsolutePath()} copied to ${server}")
				transferred++
			}
		} finally {
			FileUtils.close(instream)
		}
		success
	}

	/**
	 * Creates all parent directories specified in a complete relative
	 * pathname. Attempts to create existing directories will not cause
	 * errors.
	 *
	 * @param filename the name of the file whose parents should be created.
	 * @throws GradleException if it is impossible to cd to a remote directory
	 *
	 */
	private void createParents(String filename) throws GradleException {
		File dir = new File(filename)
		if (dirCache.contains(dir)) {
			return
		}

		List parents = []
		String dirname

		while ((dirname = dir.getParent()) != null) {
			File checkDir = new File(dirname)
			if (dirCache.contains(checkDir)) {
				break
			}
			dir = checkDir
			parents.add(dir)
		}

		// find first non cached dir
		int i = parents.size() - 1

		if (i >= 0) {
			String cwd = ftp.printWorkingDirectory()
			String parent = dir.getParent()
			if (parent != null) {
				if (!ftp.changeWorkingDirectory(resolveRemotePath(parent))) {
					throw new GradleException("could not change to directory: ${ftp.getReplyString()}")
				}
			}

			while (i >= 0) {
				dir = (File) parents.get(i--)
				// check if dir exists by trying to change into it.
				if (!ftp.changeWorkingDirectory(dir.getName())) {
					// could not change to it - try to create it
					log.debug("creating remote directory ${resolveRemotePath(dir.getPath())}")
					if (!ftp.makeDirectory(dir.getName())) {
						handleMkDirFailure()
					}
					if (!ftp.changeWorkingDirectory(dir.getName())) {
						throw new GradleException("could not change to directory: ${ftp.getReplyString()}")
					}
				}
				dirCache.add(dir)
			}
			ftp.changeWorkingDirectory(cwd)
		}
	}

	DirectoryScanner getRemoteDirectoryScanner(String remoteDir) {
		new FtpDirectoryScanner(ftp, remoteDir, remoteFileSep)
	}

	/**
	 * @see com.github.mlueders.gradle.ftp.LastModifiedChecker#timeDiffAuto
	 */
	void setTimeDiffAuto(boolean timeDiffAuto) {
		lastModifiedChecker.setTimeDiffAuto(timeDiffAuto)
	}

	/**
	 * Retrieve an object which encapsulates the last modified times of a file, both local and remote
	 * @return An instance for detecting whether the local or remote file is older or null if either there
	 * was no remote file or the last modification time could not be established
	 */
	LastModifiedCheck getLastModifiedCheck(String localDir, String filePath, TimestampGranularity granularity) {
		File localFile = new File(localDir, filePath)
		String remoteFilePath = resolveRemotePath(filePath)
		lastModifiedChecker.getLastModifiedCheck(localFile, remoteFilePath, granularity)
	}

	String getFtpReplyString() {
		ftp.getReplyString()
	}

}

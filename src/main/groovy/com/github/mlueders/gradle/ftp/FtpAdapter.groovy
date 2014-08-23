package com.github.mlueders.gradle.ftp

import groovy.util.logging.Slf4j
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPClientConfig
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
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
	private int transferred = 0
	private int skipped = 0
	private Set dirCache = new HashSet()

	public FtpAdapter(Config config) {
		this.config = config
		this.ftp = new FTPClient()
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

	void open() {
		checkAttributes()

		if (clientConfig) {
			ftp.configure(clientConfig)
		}

		log.debug("opening FTP connection to ${server}")
		ftp.setRemoteVerificationEnabled(enableRemoteVerification)
		ftp.connect(server, port)
		if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
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
		if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
			throw new GradleException("could not set transfer type: ${ftp.getReplyString()}")
		}

		if (passive) {
			log.debug("entering passive mode")
			ftp.enterLocalPassiveMode()
			if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
				throw new GradleException("could not enter into passive mode: ${ftp.getReplyString()}")
			}
		}

        // If an initial command was configured then send it.
        // Some FTP servers offer different modes of operation,
        // E.G. switching between a UNIX file system mode and a legacy file system.
        if (this.initialSiteCommand != null) {
            // TODO: retryable
            doSiteCommand(initialSiteCommand)
        }

        // For a unix ftp server you can set the default mask for all files created.
        if (umask != null) {
            // TODO: retryable
            doSiteCommand("umask " + umask)
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

	/**
	 * Sends a site command to the ftp server
	 * @param theCMD command to execute
	 */
	void doSiteCommand(String theCMD) {
		boolean rc
		String[] myReply

		log.debug("Doing Site Command: ${theCMD}")

		rc = ftp.sendSiteCommand(theCMD)

		if (!rc) {
			log.warn("Failed to issue Site Command: ${theCMD}")
		} else {
			myReply = ftp.getReplyStrings()

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
	void makeRemoteDir(String dir) throws GradleException {
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
	 * Creates all parent directories specified in a complete relative
	 * pathname. Attempts to create existing directories will not cause
	 * errors.
	 *
	 * @param ftp the FTP client instance to use to execute FTP actions on
	 *        the remote server.
	 * @param filename the name of the file whose parents should be created.
	 * @throws GradleException if it is impossible to cd to a remote directory
	 *
	 */
	void createParents(String filename) throws GradleException {
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
				if (!ftp.changeWorkingDirectory(resolveFile(parent))) {
					throw new GradleException("could not change to directory: ${ftp.getReplyString()}")
				}
			}

			while (i >= 0) {
				dir = (File) parents.get(i--)
				// check if dir exists by trying to change into it.
				if (!ftp.changeWorkingDirectory(dir.getName())) {
					// could not change to it - try to create it
					log.debug("creating remote directory ${resolveFile(dir.getPath())}")
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
		ftp.changeWorkingDirectory(workingDir)
		if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
			throw new GradleException("could not change remote directory: ${ftp.getReplyString()}")
		}
	}

	/**
	 * auto find the time difference between local and remote
	 * @return number of millis to add to remote time to make it comparable to local time
	 * @since ant 1.6
	 */
	long getTimeDiff() {
		long returnValue = 0
		File tempFile = findTempFileName()
		try {
			long localTimeStamp = tempFile.lastModified()
			BufferedInputStream instream = new BufferedInputStream(new FileInputStream(tempFile))
			ftp.storeFile(tempFile.getName(), instream)
			instream.close()
			boolean success = FTPReply.isPositiveCompletion(ftp.getReplyCode())
			if (success) {
				FTPFile[] ftpFiles = ftp.listFiles(tempFile.getName())
				if (ftpFiles.length == 1) {
					long remoteTimeStamp = ftpFiles[0].getTimestamp().getTime().getTime()
					returnValue = localTimeStamp - remoteTimeStamp
				}
				ftp.deleteFile(ftpFiles[0].getName())
			}
			tempFile.delete()
		} catch (Exception e) {
			throw new GradleException("Failed to auto calculate time difference", e)
		}
		return returnValue
	}

	/**
	 *  find a suitable name for local and remote temporary file
	 */
	private File findTempFileName() {
		FTPFile[] theFiles = null
		final int maxIterations = 1000
		for (int counter = 1; counter < maxIterations; counter++) {
			File localFile = File.createTempFile("ant${Integer.toString(counter)}", "tmp")
			String fileName = localFile.getName()
			boolean found = false
			if (theFiles == null) {
				theFiles = ftp.listFiles()
			}
			for (int counter2 = 0; counter2 < theFiles.length; counter2++) {
				if (theFiles[counter2] != null
						&& theFiles[counter2].getName().equals(fileName)) {
					found = true
					break
				}
			}
			if (!found) {
				localFile.deleteOnExit()
				return localFile
			}
		}
		throw new GradleException("Failed to find suitable remote file within ${maxIterations} iterations")
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

		if (!ftp.removeDirectory(resolveFile(dirpath))) {
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
	private String resolveFile(String file) {
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

		FTPFile[] ftpfiles = ftp.listFiles(resolveFile(filename))
		if (ftpfiles != null && ftpfiles.length > 0) {
			listingFile << "${ftpfiles[0].toString()}${LINE_SEPARATOR}"
			transferred++
		}
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

		if (!ftp.deleteFile(resolveFile(filename))) {
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

}

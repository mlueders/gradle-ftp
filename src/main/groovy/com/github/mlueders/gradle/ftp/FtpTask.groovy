package com.github.mlueders.gradle.ftp

import groovy.util.logging.Slf4j

import java.text.SimpleDateFormat
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPClientConfig
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import org.apache.tools.ant.DirectoryScanner
import org.apache.tools.ant.taskdefs.Delete
import org.apache.tools.ant.types.FileSet
import org.apache.tools.ant.util.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException

/**
 * Basic FTP client. Performs the following actions:
 * <ul>
 *   <li> <strong>send</strong> - send files to a remote server. This is the
 *   default action.</li>
 *   <li> <strong>get</strong> - retrieve files from a remote server.</li>
 *   <li> <strong>del</strong> - delete files from a remote server.</li>
 *   <li> <strong>list</strong> - create a file listing.</li>
 *   <li> <strong>chmod</strong> - change unix file permissions.</li>
 *   <li> <strong>rmdir</strong> - remove directories, if empty, from a
 *   remote server.</li>
 * </ul>
 * <strong>Note:</strong> Some FTP servers - notably the Solaris server - seem
 * to hold data ports open after a "retr" operation, allowing them to timeout
 * instead of shutting them down cleanly. This happens in active or passive
 * mode, and the ports will remain open even after ending the FTP session. FTP
 * "send" operations seem to close ports immediately. This behavior may cause
 * problems on some systems when downloading large sets of files.
 *
 * @since Ant 1.3
 */
@Slf4j
public class FtpTask extends DefaultTask {

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
	 * an action to perform, one of
	 * "send", "put", "recv", "get", "del", "delete", "list", "mkdir", "chmod",
	 * "rmdir"
	 */
	public static enum Action {
		SEND_FILES("sending", "sent", "files"),
		GET_FILES("getting", "retrieved", "files"),
		DEL_FILES("deleting", "deleted", "files"),
		LIST_FILES("listing", "listed", "files"),
		MK_DIR("making directory", "created directory", "directory"),
		CHMOD("chmod", "mode changed", "files"),
		RM_DIR("removing", "removed", "directories"),
		SITE_CMD("site", "site command executed", "site command")

		private String actionString;
		private String completedString;
		private String targetString;

		private Action(String actionString, String completedString, String targetString) {
			this.actionString = actionString;
			this.completedString = completedString;
			this.targetString = targetString;
		}

		String getActionString() {
			return actionString
		}

		String getCompletedString() {
			return completedString
		}

		String getTargetString() {
			return targetString
		}
	}

	/**
	 * Represents one of the valid timestamp adjustment values
	 *
	 * A timestamp adjustment may be used in file transfers for checking
	 * uptodateness. MINUTE means to add one minute to the server
	 * timestamp.  This is done because FTP servers typically list
	 * timestamps HH:mm and client FileSystems typically use HH:mm:ss.
	 *
	 * The default is to use MINUTE for PUT actions and NONE for GET
	 * actions, since GETs have the <code>preserveLastModified</code>
	 * option, which takes care of the problem in most use cases where
	 * this level of granularity is an issue.
	 */
	public static enum Granularity {
		UNSET,
		MINUTE,
		NONE

		public long getMilliseconds(Action action) {
			if ((this == MINUTE) || ((this == UNSET) && (action == Action.SEND_FILES))) {
				return GRANULARITY_MINUTE
			}
			return 0L
		}
	}


	/** Default port for FTP */
	public static final int DEFAULT_FTP_PORT = 21

	/**
	 * The FTP action to be taken.
	 * Defaults to send.
	 */
    Action action = Action.SEND_FILES
	/**
     * The remote directory where files will be placed. This may be a
     * relative or absolute path, and must be in the path syntax expected by
     * the remote server. No correction of path syntax will be performed.
	 */
    String remoteDir
	/**
	 * The output file for the "list" action. This attribute is ignored for any other actions.
	 */
    File listing
	/**
	 * Names the command that will be executed if the action is "site".
	 */
	String siteCommand
	/**
	 * Names a site command that will be executed immediately after connection.
	 */
	String initialSiteCommand
	/**
	 * If true, transmit only files that are new or changed from their remote counterparts.
	 * Defaults to false, transmit all files.
	 * See the related attributes <code>timeDiffMillis</code> and <code>timeDiffAuto</code>.
	 */
    boolean newerOnly = false
	/**
	 * Number of milliseconds to add to the time on the remote machine to get the time on the local machine.
	 * Use in conjunction with <code>newerOnly</code>
	 */
	long timeDiffMillis = 0
	/**
	 * Automatically determine the time difference between local and remote machine, defaults to false.
	 *
	 * This requires right to create and delete a temporary file in the remote directory.
	 */
	boolean timeDiffAuto = false
	/**
	 * Used in conjunction with <code>newerOnly</code>
	 * @see Granularity
	 */
	Granularity timestampGranularity = Granularity.UNSET
	/**
	 * If true, unsuccessful file put, delete and get operations to be skipped with a warning
	 * and the remainder of the files still transferred.
	 * Defaults to false.
	 */
	boolean skipFailedTransfers = false
	/**
	 * If true, skip errors on directory creation.
	 * Defaults to false.
	 */
    boolean ignoreNoncriticalErrors = false
	/**
	 * If true, modification times for "gotten" files will be preserved.
	 * Defaults to false.
	 */
	boolean preserveLastModified = false
	/**
	 * The file permission mode (Unix only) for files sent to the server.
	 */
	String chmod = null
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
	/**
	 * Defines how many times to retry executing FTP command before giving up.
	 * A negative value means keep trying forever.
	 * Default is 0 - try once and give up if failure.
	 */
    int retriesAllowed = 0




	/** return code of ftp */
	private static final int CODE_521 = 521
	private static final int CODE_550 = 550
	private static final int CODE_553 = 553

	/** adjust uptodate calculations where server timestamps are HH:mm and client's
	 * are HH:mm:ss */
	private static final long GRANULARITY_MINUTE = 60000L

	/** Date formatter used in logging, note not thread safe! */
	private static final SimpleDateFormat TIMESTAMP_LOGGING_SDF =
			new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

	private static final FileUtils FILE_UTILS = FileUtils.getFileUtils()

	private Set dirCache = new HashSet()
	private int transferred = 0
	private int skipped = 0
	private long granularityMillis = 0L

	/**
	 * Checks to see that all required parameters are set.
	 *
	 * @throws GradleException if the configuration is not valid.
	 */
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

		if ((action == Action.LIST_FILES) && (listing == null)) {
			throw new GradleException("listing attribute must be set for list action!")
		}
		if (action == Action.MK_DIR && remoteDir == null) {
			throw new GradleException("remotedir attribute must be set for mkdir action!")
		}
		if (action == Action.CHMOD && chmod == null) {
			throw new GradleException("chmod attribute must be set for chmod action!")
		}
		if (action == Action.SITE_CMD && siteCommand == null) {
			throw new GradleException("sitecommand attribute must be set for site action!")
		}
	}


    /**
     * For each file in the fileset, do the appropriate action: send, get,
     * delete, or list.
     *
     * @param ftp the FTPClient instance used to perform FTP actions
     * @param fs the fileset on which the actions are performed.
     *
     * @return the number of files to be transferred.
     *
     * @throws IOException if there is a problem reading a file
     * @throws GradleException if there is a problem in the configuration.
     */
    protected int transferFiles(final FTPClient ftp, FileSet fs) throws IOException, GradleException {
        DirectoryScanner ds
        if (action == Action.SEND_FILES) {
            ds = fs.getDirectoryScanner(getAntProject())
        } else {
            ds = new FtpDirectoryScanner(ftp, remoteDir, remoteFileSep)
            fs.setupDirectoryScanner(ds, getAntProject())
            ds.setFollowSymlinks(fs.isFollowSymlinks())
            ds.scan()
        }

        String[] dsfiles
        if (action == Action.RM_DIR) {
            dsfiles = ds.getIncludedDirectories()
        } else {
            dsfiles = ds.getIncludedFiles()
        }
        String dir = null

        if ((ds.getBasedir() == null)
            && ((action == Action.SEND_FILES) || (action == Action.GET_FILES))) {
            throw new GradleException("the dir attribute must be set for send and get actions")
        } else {
            if ((action == Action.SEND_FILES) || (action == Action.GET_FILES)) {
                dir = ds.getBasedir().getAbsolutePath()
            }
        }

        // If we are doing a listing, we need the output stream created now.
        BufferedWriter bw = null

        try {
            if (action == Action.LIST_FILES) {
                File pd = listing.getParentFile()

                if (!pd.exists()) {
                    pd.mkdirs()
                }
                bw = new BufferedWriter(new FileWriter(listing))
            }
            if (action == Action.RM_DIR) {
                // to remove directories, start by the end of the list
                // the trunk does not let itself be removed before the leaves
                for (int i = dsfiles.length - 1; i >= 0; i--) {
	                // TODO: retryable
	                rmDir(ftp, dsfiles[i])
                }
            } else {
                final BufferedWriter fbw = bw
                final String fdir = dir
                if (this.newerOnly) {
                    this.granularityMillis = this.timestampGranularity.getMilliseconds(action)
                }
                for (int i = 0; i < dsfiles.length; i++) {
                    final String dsfile = dsfiles[i]
	                // TODO: retryable
	                switch (action) {
		                case Action.SEND_FILES:
			                sendFile(ftp, fdir, dsfile)
			                break
		                case Action.GET_FILES:
			                getFile(ftp, fdir, dsfile)
			                break
		                case Action.DEL_FILES:
			                delFile(ftp, dsfile)
			                break
		                case Action.LIST_FILES:
			                listFile(ftp, fbw, dsfile)
			                break
		                case Action.CHMOD:
			                doSiteCommand(ftp, "chmod ${chmod} ${resolveFile(dsfile)}")
			                transferred++
			                break
		                default:
			                throw new GradleException("unknown ftp action ${action}")
	                }
                }
            }
        } finally {
            FileUtils.close(bw)
        }

        return dsfiles.length
    }


	public void fileset(String dirName, Closure cl) {
		filesets.add(ant.fileset(dir: dirName, cl))
	}

	public void fileset(Closure cl) {
		filesets.add(ant.fileset(cl))
	}

	public void fileset(FileSet fileset) {
		filesets.add(fileset)
	}

	private Vector filesets = new Vector()
    /**
     * Sends all files specified by the configured filesets to the remote server.
     *
     * @param ftp the FTPClient instance used to perform FTP actions
     *
     * @throws IOException if there is a problem reading a file
     * @throws GradleException if there is a problem in the configuration.
     */
    protected void transferFiles(FTPClient ftp) throws IOException, GradleException {
        transferred = 0
        skipped = 0

        if (filesets.size() == 0) {
            throw new GradleException("at least one fileset must be specified.")
        } else {
            // get files from filesets
            final int size = filesets.size()
            for (int i = 0; i < size; i++) {
                FileSet fs = (FileSet) filesets.elementAt(i)

                if (fs != null) {
                    transferFiles(ftp, fs)
                }
            }
        }

        log.info("${transferred} ${action.targetString} ${action.completedString}")
        if (skipped != 0) {
            log.info("${skipped} ${action.targetString} were not successfully ${action.completedString}")
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
    protected String resolveFile(String file) {
        return file.replace(System.getProperty("file.separator").charAt(0), remoteFileSep.charAt(0))
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
    protected void createParents(FTPClient ftp, String filename) throws GradleException {
        File dir = new File(filename)
        if (dirCache.contains(dir)) {
            return
        }

        Vector parents = new Vector()
        String dirname

        while ((dirname = dir.getParent()) != null) {
            File checkDir = new File(dirname)
            if (dirCache.contains(checkDir)) {
                break
            }
            dir = checkDir
            parents.addElement(dir)
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
                dir = (File) parents.elementAt(i--)
                // check if dir exists by trying to change into it.
                if (!ftp.changeWorkingDirectory(dir.getName())) {
                    // could not change to it - try to create it
                    log.debug("creating remote directory ${resolveFile(dir.getPath())}")
                    if (!ftp.makeDirectory(dir.getName())) {
                        handleMkDirFailure(ftp)
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
     * auto find the time difference between local and remote
     * @param ftp handle to ftp client
     * @return number of millis to add to remote time to make it comparable to local time
     * @since ant 1.6
     */
    private long getTimeDiff(FTPClient ftp) {
        long returnValue = 0
        File tempFile = findFileName(ftp)
        try {
            // create a local temporary file
            FILE_UTILS.createNewFile(tempFile)
            long localTimeStamp = tempFile.lastModified()
            BufferedInputStream instream = new BufferedInputStream(new FileInputStream(tempFile))
            ftp.storeFile(tempFile.getName(), instream)
            instream.close()
            boolean success = FTPReply.isPositiveCompletion(ftp.getReplyCode())
            if (success) {
                FTPFile [] ftpFiles = ftp.listFiles(tempFile.getName())
                if (ftpFiles.length == 1) {
                    long remoteTimeStamp = ftpFiles[0].getTimestamp().getTime().getTime()
                    returnValue = localTimeStamp - remoteTimeStamp
                }
                ftp.deleteFile(ftpFiles[0].getName())
            }
            // delegate the deletion of the local temp file to the delete task
            // because of race conditions occuring on Windows
            Delete mydelete = new Delete()
            mydelete.bindToOwner(this)
            mydelete.setFile(tempFile.getCanonicalFile())
            mydelete.execute()
        } catch (Exception e) {
            throw new GradleException("Failed to auto calculate time difference", e)
        }
        return returnValue
    }
    /**
     *  find a suitable name for local and remote temporary file
     */
    private File findFileName(FTPClient ftp) {
        FTPFile [] theFiles = null
        final int maxIterations = 1000
        for (int counter = 1; counter < maxIterations; counter++) {
            File localFile = FILE_UTILS.createTempFile(
                                                       "ant" + Integer.toString(counter), ".tmp",
                                                       null, false, false)
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
        return null
    }

    /**
     * Checks to see if the remote file is current as compared with the local
     * file. Returns true if the target file is up to date.
     * @param ftp ftpclient
     * @param localFile local file
     * @param remoteFile remote file
     * @return true if the target file is up to date
     * @throws GradleException if the date of the remote files cannot be found and the action is
     * GET_FILES
     */
    protected boolean isUpToDate(FTPClient ftp, File localFile, String remoteFile) {
        log.debug("checking date for ${remoteFile}")

        FTPFile[] files = ftp.listFiles(remoteFile)

        // For Microsoft's Ftp-Service an Array with length 0 is
        // returned if configured to return listings in "MS-DOS"-Format
        if (files == null || files.length == 0) {
            // If we are sending files, then assume out of date.
            // If we are getting files, then throw an error

            if (action == Action.SEND_FILES) {
                log.debug("Could not date test remote file: ${remoteFile} assuming out of date.")
                return false
            } else {
                throw new GradleException("could not date test remote file: ${ftp.getReplyString()}")
            }
        }

        long remoteTimestamp = files[0].getTimestamp().getTime().getTime()
        long localTimestamp = localFile.lastModified()
        long adjustedRemoteTimestamp =
            remoteTimestamp + this.timeDiffMillis + this.granularityMillis

	    log.debug("   [${formatDate(localTimestamp)}] local")
	    String message = "   [${formatDate(adjustedRemoteTimestamp)}] remote"
        if (remoteTimestamp != adjustedRemoteTimestamp) {
	        message += " - (raw: ${formatDate(remoteTimestamp)})"
        }
	    log.debug(message)

        if (this.action == Action.SEND_FILES) {
            return adjustedRemoteTimestamp >= localTimestamp
        } else {
            return localTimestamp >= adjustedRemoteTimestamp
        }
    }

	private String formatDate(long timestamp) {
		synchronized(TIMESTAMP_LOGGING_SDF) {
			TIMESTAMP_LOGGING_SDF.format(new Date(timestamp))
		}
	}


    /**
     * Sends a site command to the ftp server
     * @param ftp ftp client
     * @param theCMD command to execute
     */
    protected void doSiteCommand(FTPClient ftp, String theCMD) {
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
     * Sends a single file to the remote host. <code>filename</code> may
     * contain a relative path specification. When this is the case, <code>sendFile</code>
     * will attempt to create any necessary parent directories before sending
     * the file. The file will then be sent using the entire relative path
     * spec - no attempt is made to change directories. It is anticipated that
     * this may eventually cause problems with some FTP servers, but it
     * simplifies the coding.
     * @param ftp ftp client
     * @param dir base directory of the file to be sent (local)
     * @param filename relative path of the file to be send
     *        locally relative to dir
     *        remotely relative to the remotedir attribute
     */
    protected void sendFile(FTPClient ftp, String dir, String filename) {
        InputStream instream = null

        try {
            // TODO - why not simply new File(dir, filename)?
            File file = getAntProject().resolveFile(new File(dir, filename).getPath())

            if (newerOnly && isUpToDate(ftp, file, resolveFile(filename))) {
                return
            }

            if (verbose) {
                log.info("transferring ${file.getAbsolutePath()}")
            }

            instream = new BufferedInputStream(new FileInputStream(file))

            createParents(ftp, filename)

            ftp.storeFile(resolveFile(filename), instream)

            boolean success = FTPReply.isPositiveCompletion(ftp.getReplyCode())

            if (!success) {
                String s = "could not put file: " + ftp.getReplyString()

                if (skipFailedTransfers) {
                    log.warn(s)
                    skipped++
                } else {
                    throw new GradleException(s)
                }

            } else {
                // see if we should issue a chmod command
                if (chmod != null) {
                    doSiteCommand(ftp, "chmod " + chmod + " " + resolveFile(filename))
                }
                log.debug("File ${file.getAbsolutePath()} copied to ${server}")
                transferred++
            }
        } finally {
            FileUtils.close(instream)
        }
    }


    /**
     * Delete a file from the remote host.
     * @param ftp ftp client
     * @param filename file to delete
     * @throws GradleException if skipFailedTransfers is set to false
     * and the deletion could not be done
     */
    protected void delFile(FTPClient ftp, String filename) throws GradleException {
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

    /**
     * Delete a directory, if empty, from the remote host.
     * @param ftp ftp client
     * @param dirname directory to delete
     * @throws GradleException if skipFailedTransfers is set to false
     * and the deletion could not be done
     */
    protected void rmDir(FTPClient ftp, String dirname) throws GradleException {
        if (verbose) {
            log.info("removing ${dirname}")
        }

        if (!ftp.removeDirectory(resolveFile(dirname))) {
            String s = "could not remove directory: " + ftp.getReplyString()

            if (skipFailedTransfers) {
                log.warn(s)
                skipped++
            } else {
                throw new GradleException(s)
            }
        } else {
            log.debug("Directory ${dirname} removed from ${server}")
            transferred++
        }
    }

	private org.apache.tools.ant.Project getAntProject() {
		project.ant.project
	}

    /**
     * Retrieve a single file from the remote host. <code>filename</code> may
     * contain a relative path specification. <p>
     *
     * The file will then be retrieved using the entire relative path spec -
     * no attempt is made to change directories. It is anticipated that this
     * may eventually cause problems with some FTP servers, but it simplifies
     * the coding.</p>
     * @param ftp the ftp client
     * @param dir local base directory to which the file should go back
     * @param filename relative path of the file based upon the ftp remote directory
     *        and/or the local base directory (dir)
     * @throws GradleException if skipFailedTransfers is false
     * and the file cannot be retrieved.
     */
    protected void getFile(FTPClient ftp, String dir, String filename) throws GradleException {
        OutputStream outstream = null
        try {
            File file = getAntProject().resolveFile(new File(dir, filename).getPath())

            if (newerOnly && isUpToDate(ftp, file, resolveFile(filename))) {
                return
            }

            if (verbose) {
                log.info("transferring ${filename} to ${file.getAbsolutePath()}")
            }

            File pdir = file.getParentFile()

            if (!pdir.exists()) {
                pdir.mkdirs()
            }
            outstream = new BufferedOutputStream(new FileOutputStream(file))
            ftp.retrieveFile(resolveFile(filename), outstream)

            if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
                String s = "could not get file: " + ftp.getReplyString()

                if (skipFailedTransfers) {
                    log.warn(s)
                    skipped++
                } else {
                    throw new GradleException(s)
                }

            } else {
                log.debug("File ${file.getAbsolutePath()} copied from ${server}")
                transferred++
                if (preserveLastModified) {
                    outstream.close()
                    outstream = null
                    FTPFile[] remote = ftp.listFiles(resolveFile(filename))
                    if (remote.length > 0) {
                        FILE_UTILS.setFileLastModified(file,
                                                       remote[0].getTimestamp()
                                                       .getTime().getTime())
                    }
                }
            }
        } finally {
            FileUtils.close(outstream)
        }
    }


    /**
     * List information about a single file from the remote host. <code>filename</code>
     * may contain a relative path specification. <p>
     *
     * The file listing will then be retrieved using the entire relative path
     * spec - no attempt is made to change directories. It is anticipated that
     * this may eventually cause problems with some FTP servers, but it
     * simplifies the coding.</p>
     * @param ftp ftp client
     * @param bw buffered writer
     * @param filename the directory one wants to list
     */
	protected void listFile(FTPClient ftp, BufferedWriter bw, String filename) {
        if (verbose) {
            log.info("listing ${filename}")
        }
        FTPFile[] ftpfiles = ftp.listFiles(resolveFile(filename))

        if (ftpfiles != null && ftpfiles.length > 0) {
            bw.write(ftpfiles[0].toString())
            bw.newLine()
            transferred++
        }
    }


    /**
     * Create the specified directory on the remote host.
     *
     * @param ftp The FTP client connection
     * @param dir The directory to create (format must be correct for host
     *      type)
     * @throws GradleException if ignoreNoncriticalErrors has not been set to true
     *         and a directory could not be created, for instance because it was
     *         already existing. Precisely, the codes 521, 550 and 553 will trigger
     *         a GradleException
     */
    protected void makeRemoteDir(FTPClient ftp, String dir) throws GradleException {
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
                    // codes 521, 550 and 553 can be produced by FTP Servers
                    //  to indicate that an attempt to create a directory has
                    //  failed because the directory already exists.
                    int rc = ftp.getReplyCode()
                    if (!(ignoreNoncriticalErrors && (rc == CODE_550 || rc == CODE_553 || rc == CODE_521))) {
                        throw new GradleException("could not create directory: ${ftp.getReplyString()}")
                    }
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
     * @param ftp current ftp connection
     * @throws GradleException if this is an error to signal
     */
    private void handleMkDirFailure(FTPClient ftp) throws GradleException {
        int rc = ftp.getReplyCode()
        if (!(ignoreNoncriticalErrors && (rc == CODE_550 || rc == CODE_553 || rc == CODE_521))) {
            throw new GradleException("could not create directory: ${ftp.getReplyString()}")
        }
    }

    /**
     * Runs the task.
     *
     * @throws GradleException if the task fails or is not configured correctly.
     */
    public void executeTask() throws GradleException {
        checkAttributes()

        FTPClient ftp = null

        try {
            ftp = new FTPClient()
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
	            doSiteCommand(ftp, initialSiteCommand)
            }

            // For a unix ftp server you can set the default mask for all files created.
            if (umask != null) {
	            // TODO: retryable
	            doSiteCommand(ftp, "umask " + umask)
            }

            // If the action is Action.MK_DIR, then the specified remote
            // directory is the directory to create.
            if (action == Action.MK_DIR) {
	            // TODO: retryable
	            makeRemoteDir(ftp, remoteDir)
            } else if (action == Action.SITE_CMD) {
	            // TODO: retryable
	            doSiteCommand(ftp, siteCommand)
            } else {
                if (remoteDir != null) {
                    log.debug("changing the remote directory to ${remoteDir}")
                    ftp.changeWorkingDirectory(remoteDir)
                    if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
                        throw new GradleException("could not change remote directory: ${ftp.getReplyString()}")
                    }
                }
                if (newerOnly && timeDiffAuto) {
                    // in this case we want to find how much time span there is between local
                    // and remote
                    timeDiffMillis = getTimeDiff(ftp)
                }
                log.info("${action.actionString} ${action.targetString}")
                transferFiles(ftp)
            }

        } catch (IOException ex) {
            throw new GradleException("error during FTP transfer: ${ex}", ex)
        } finally {
            if (ftp != null && ftp.isConnected()) {
                try {
                    log.debug("disconnecting")
                    ftp.logout()
                    ftp.disconnect()
                } catch (IOException ex) {
                    // ignore it
                }
            }
        }
    }

}

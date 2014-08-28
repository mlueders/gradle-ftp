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

import groovy.transform.EqualsAndHashCode
import groovy.util.logging.Slf4j
import org.apache.commons.net.ftp.FTPFile
import org.apache.tools.ant.DirectoryScanner
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

	@EqualsAndHashCode
	private static class TransferableFile {
		String baseDir
		String relativePath
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
	public static enum TimestampGranularity {
		UNSET,
		MINUTE,
		NONE

		private static final long GRANULARITY_MINUTE = 60000L

		public long getMilliseconds(Action action) {
			if ((this == MINUTE) || ((this == UNSET) && (action == Action.SEND_FILES))) {
				return GRANULARITY_MINUTE
			}
			return 0L
		}
	}


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
	 * If true, transmit only files that are new or changed from their remote counterparts.
	 * Defaults to false, transmit all files.
	 * See the related attributes <code>timeDiffMillis</code> and <code>timeDiffAuto</code>.
	 */
    boolean newerOnly = false
	/**
	 * Used in conjunction with <code>newerOnly</code>
	 * @see TimestampGranularity
	 */
	TimestampGranularity timestampGranularity = TimestampGranularity.UNSET
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
	 * Defines how many times to retry executing FTP command before giving up.
	 * A negative value means keep trying forever.
	 * Default is 0 - try once and give up if failure.
	 */
    int retriesAllowed = 0


	@Delegate
	private FtpAdapter.Config config = new FtpAdapter.Config()
	protected FtpAdapter ftpAdapter
	private List<FileSet> filesets = []
	private RetryHandler retryHandler

	public void fileset(String dirName, Closure cl) {
		fileset(ant.fileset(dir: dirName, cl))
	}

	public void fileset(Closure cl) {
		fileset(ant.fileset(cl))
	}

	public void fileset(FileSet fileset) {
		filesets.add(fileset)
	}

	/**
	 * Checks to see that all required parameters are set.
	 *
	 * @throws GradleException if the configuration is not valid.
	 */
	private void checkAttributes() throws GradleException {
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
     * @param fs the fileset on which the actions are performed.
     * @return the number of files to be transferred.
     * @throws IOException if there is a problem reading a file
     * @throws GradleException if there is a problem in the configuration.
     */
    protected int transferFiles(List<TransferableFile> filesToTransfer) throws IOException, GradleException {
	    // If we are doing a listing, we need the output stream created now.
        BufferedWriter bw = null

        try {
            if (action == Action.RM_DIR) {
                // to remove directories, start by the end of the list
                // the trunk does not let itself be removed before the leaves
	            for (TransferableFile file : filesToTransfer.reverse()) {
		            retryHandler.execute(file.relativePath) {
			            ftpAdapter.rmDir(file.relativePath)
		            }
                }
            } else {
                if (this.newerOnly) {
	                ftpAdapter.granularityMillis = this.timestampGranularity.getMilliseconds(action)
                }
	            for (TransferableFile file : filesToTransfer) {
		            retryHandler.execute(file.relativePath) {
			            switch (action) {
				            case Action.SEND_FILES:
					            sendFile(file.baseDir, file.relativePath)
					            break
				            case Action.GET_FILES:
					            getFile(file.baseDir, file.relativePath)
					            break
				            case Action.DEL_FILES:
					            ftpAdapter.deleteFile(file.relativePath)
					            break
				            case Action.LIST_FILES:
					            ftpAdapter.listFile(listing, file.relativePath)
					            break
				            case Action.CHMOD:
					            ftpAdapter.chmod(chmod, file.relativePath)
					            // TODO: transferred++
					            break
				            default:
					            throw new GradleException("unknown ftp action ${action}")
			            }
		            }
                }
            }
        } finally {
            FileUtils.close(bw)
        }

        return filesToTransfer.size()
    }

    /**
     * Sends all files specified by the configured filesets to the remote server.
     *
     * @throws IOException if there is a problem reading a file
     * @throws GradleException if there is a problem in the configuration.
     */
    protected void transferFiles() throws IOException, GradleException {
	    List<TransferableFile> filesToTransfer = getFilesToTransfer()
	    if (filesToTransfer.isEmpty()) {
		    throw new GradleException("at least one fileset must be specified.")
	    }

	    transferFiles(filesToTransfer)

        log.info("${ftpAdapter.transferred} ${action.targetString} ${action.completedString}")
        if (ftpAdapter.skipped != 0) {
            log.info("${ftpAdapter.skipped} ${action.targetString} were not successfully ${action.completedString}")
        }
    }

	private List<TransferableFile> getFilesToTransfer() {
		List<TransferableFile> filesToTransfer = []
		for (FileSet fs : filesets) {
			filesToTransfer.addAll(getFilesToTransfer(fs))
		}
		filesToTransfer.unique()
	}

	private List<TransferableFile> getFilesToTransfer(FileSet fileset) {
		DirectoryScanner ds
		if (action == Action.SEND_FILES) {
			ds = fileset.getDirectoryScanner(project.ant.project)
		} else {
			ds = ftpAdapter.getRemoteDirectoryScanner(remoteDir)
			fileset.setupDirectoryScanner(ds, project.ant.project)
			ds.setFollowSymlinks(fileset.isFollowSymlinks())
			ds.scan()
		}

		String baseDir = null
		if ((action == Action.SEND_FILES) || (action == Action.GET_FILES)) {
			if (ds.getBasedir() != null) {
				baseDir = ds.getBasedir().getAbsolutePath()
			} else {
				throw new GradleException("the dir attribute must be set for send and get actions")
			}
		}

		String[] dsfiles
		if (action == Action.RM_DIR) {
			dsfiles = ds.getIncludedDirectories()
		} else {
			dsfiles = ds.getIncludedFiles()
		}
		dsfiles.collect { String relativePath ->
			new TransferableFile(baseDir: baseDir, relativePath: relativePath)
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
     * @param dir base directory of the file to be sent (local)
     * @param filename relative path of the file to be send locally relative to dir
     *        remotely relative to the remotedir attribute
     */
    protected void sendFile(String dir, String filename) {
	    try {
		    if (newerOnly && ftpAdapter.isRemoteFileOlder(dir, filename)) {
			    return
		    }
	    } catch (GradleException ex) {
		    log.debug("Could not date test remote file: ${filename} assuming out of date.")
	    }

	    boolean success = ftpAdapter.sendFile(dir, filename)
        // see if we should issue a chmod command
        if (success && (chmod != null)) {
	        ftpAdapter.chmod(chmod, filename)
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
     * @throws GradleException if skipFailedTransfers is false
     * and the file cannot be retrieved.
     */
	protected void getFile(String dir, String filename) throws GradleException {
		if (newerOnly && ftpAdapter.isLocalFileOlder(dir, filename)) {
			return
		}

		File transferredFile = ftpAdapter.getFile(dir, filename)
		if (preserveLastModified && (transferredFile != null)) {
			FTPFile[] remote = ftpAdapter.listFiles(filename)
			if (remote.length > 0) {
				transferredFile.setLastModified(remote[0].getTimestamp().getTime().getTime())
			}
		}
	}

	/**
     * Runs the task.
     *
     * @throws GradleException if the task fails or is not configured correctly.
     */
    public void executeTask() throws GradleException {
	    checkAttributes()
	    ftpAdapter = new FtpAdapter(config)
	    retryHandler = new RetryHandler(retriesAllowed, log)

        try {
	        ftpAdapter.open(retryHandler)

            // If the action is Action.MK_DIR, then the specified remote
            // directory is the directory to create.
            if (action == Action.MK_DIR) {
	            retryHandler.execute(remoteDir) {
		            ftpAdapter.mkDir(remoteDir)
	            }
            } else if (action == Action.SITE_CMD) {
	            retryHandler.execute("Site Command: ${siteCommand}") {
		            ftpAdapter.doSiteCommand(siteCommand)
	            }
            } else {
                if (remoteDir != null) {
	                ftpAdapter.changeWorkingDirectory(remoteDir)
                }
                log.info("${action.actionString} ${action.targetString}")
                transferFiles()
            }
        } catch (IOException ex) {
            throw new GradleException("error during FTP transfer: ${ex}", ex)
        } finally {
	        ftpAdapter.close()
        }
    }

}

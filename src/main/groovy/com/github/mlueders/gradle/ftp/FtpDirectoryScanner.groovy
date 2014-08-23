package com.github.mlueders.gradle.ftp

import groovy.util.logging.Slf4j
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.tools.ant.DirectoryScanner
import org.apache.tools.ant.types.selectors.SelectorUtils
import org.apache.tools.ant.util.FileUtils
import org.apache.tools.ant.util.VectorSet
import org.gradle.api.GradleException

/**
 * internal class allowing to read the contents of a remote file system
 * using the FTP protocol used in particular for ftp get operations
 * differences with DirectoryScanner
 * "" (the root of the fileset) is never included in the included directories
 * followSymlinks defaults to false
 */
@Slf4j
class FtpDirectoryScanner extends DirectoryScanner {

	private FTPClient ftp = null
	private String rootPath = null
	private String remoteDir
	private String remoteFileSep

	/**
	 * since ant 1.6
	 * this flag should be set to true on UNIX and can save scanning time
	 */
	private boolean remoteSystemCaseSensitive = false
	private boolean remoteSensitivityChecked = false

	/**
	 * constructor
	 * @param ftp ftpclient object
	 */
	public FtpDirectoryScanner(FTPClient ftp, String remoteDir, String remoteFileSep) {
		this.ftp = ftp
		this.remoteDir = remoteDir
		this.remoteFileSep = remoteFileSep
		this.setFollowSymlinks(false)
	}

	/**
	 * scans the remote directory,
	 * storing internally the included files, directories, ...
	 */
	public void scan() {
		if (includes == null) {
			// No includes supplied, so set it to 'matches all'
			includes = ["**"]
		}
		if (excludes == null) {
			excludes = new String[0]
		}

		filesIncluded = new VectorSet()
		filesNotIncluded = new Vector()
		filesExcluded = new VectorSet()
		dirsIncluded = new VectorSet()
		dirsNotIncluded = new Vector()
		dirsExcluded = new VectorSet()

		try {
			String cwd = ftp.printWorkingDirectory()
			// always start from the current ftp working dir
			forceRemoteSensitivityCheck()

			checkIncludePatterns()
			clearCaches()
			ftp.changeWorkingDirectory(cwd)
		} catch (IOException e) {
			throw new GradleException("Unable to scan FTP server: ", e)
		}
	}

	/**
	 * this routine is actually checking all the include patterns in
	 * order to avoid scanning everything under base dir
	 * @since ant1.6
	 */
	private void checkIncludePatterns() {
		Hashtable newroots = new Hashtable()
		// put in the newroots vector the include patterns without wildcard tokens
		for (int icounter = 0; icounter < includes.length; icounter++) {
			String newpattern = SelectorUtils.rtrimWildcardTokens(includes[icounter])
			newroots.put(newpattern, includes[icounter])
		}
		if (remoteDir == null) {
			try {
				remoteDir = ftp.printWorkingDirectory()
			} catch (IOException e) {
				throw new GradleException("could not read current ftp directory", e)
			}
		}
		if (!ftp.changeWorkingDirectory(remoteDir)) {
			return
		}

		AntFTPFile baseFTPFile = new AntFTPRootFile(ftp, remoteDir)
		rootPath = baseFTPFile.getAbsolutePath()
		// construct it
		if (newroots.containsKey("")) {
			// we are going to scan everything anyway
			scandir(rootPath, "", true)
		} else {
			// only scan directories that can include matched files or
			// directories
			Enumeration enum2 = newroots.keys()

			while (enum2.hasMoreElements()) {
				String currentelement = (String) enum2.nextElement()
				String originalpattern = (String) newroots.get(currentelement)
				AntFTPFile myfile = new AntFTPFile(baseFTPFile, currentelement)
				boolean isOK = true
				boolean traversesSymlinks = false
				String path = null

				if (myfile.exists()) {
					forceRemoteSensitivityCheck()
					if (remoteSensitivityChecked && remoteSystemCaseSensitive && isFollowSymlinks()) {
						// cool case, we do not need to scan all the subdirs in the relative path
						path = myfile.getFastRelativePath()
					} else {
						// may be on a case insensitive file system.  We want
						// the results to show what's really on the disk, so
						// we need to double check.
						try {
							path = myfile.getRelativePath()
							traversesSymlinks = myfile.isTraverseSymlinks()
						} catch (GradleException be) {
							isOK = false
						}
					}
				} else {
					isOK = false
				}
				if (isOK) {
					currentelement = path.replace(remoteFileSep.charAt(0), File.separatorChar)
					if (!isFollowSymlinks() && traversesSymlinks) {
						continue
					}

					if (myfile.isDirectory()) {
						if (isIncluded(currentelement)
								&& currentelement.length() > 0) {
							accountForIncludedDir(currentelement, myfile, true)
						} else {
							if (currentelement.length() > 0) {
								if (currentelement.charAt(currentelement.length() - 1) != File.separatorChar) {
									currentelement = currentelement + File.separatorChar
								}
							}
							scandir(myfile.getAbsolutePath(), currentelement, true)
						}
					} else {
						if (isCaseSensitive && originalpattern.equals(currentelement)) {
							accountForIncludedFile(currentelement)
						} else if (!isCaseSensitive && originalpattern.equalsIgnoreCase(currentelement)) {
							accountForIncludedFile(currentelement)
						}
					}
				}
			}
		}
	}
	/**
	 * scans a particular directory. populates the scannedDirs cache.
	 *
	 * @param dir directory to scan
	 * @param vpath relative path to the base directory of the remote fileset
	 * always ended with a File.separator
	 * @param fast seems to be always true in practice
	 */
	protected void scandir(String dir, String vpath, boolean fast) {
		// avoid double scanning of directories, can only happen in fast mode
		if (fast && hasBeenScanned(vpath)) {
			return
		}
		try {
			if (!ftp.changeWorkingDirectory(dir)) {
				return
			}
			String completePath
			if (!vpath.equals("")) {
				completePath = rootPath + remoteFileSep + vpath.replace(File.separatorChar, remoteFileSep.charAt(0))
			} else {
				completePath = rootPath
			}
			FTPFile[] newfiles = listFiles(completePath, false)

			if (newfiles == null) {
				ftp.changeToParentDirectory()
				return
			}
			for (int i = 0; i < newfiles.length; i++) {
				FTPFile file = newfiles[i]
				if (file != null
						&& !file.getName().equals(".")
						&& !file.getName().equals("..")) {
					String name = vpath + file.getName()
					scannedDirs.put(name, new FTPFileProxy(file))
					if (isFunctioningAsDirectory(ftp, dir, file)) {
						boolean slowScanAllowed = true
						if (!isFollowSymlinks() && file.isSymbolicLink()) {
							dirsExcluded.addElement(name)
							slowScanAllowed = false
						} else if (isIncluded(name)) {
							accountForIncludedDir(name,
									new AntFTPFile(ftp, file, completePath), fast)
						} else {
							dirsNotIncluded.addElement(name)
							if (fast && couldHoldIncluded(name)) {
								scandir(file.getName(),
										name + File.separator, fast)
							}
						}
						if (!fast && slowScanAllowed) {
							scandir(file.getName(),
									name + File.separator, fast)
						}
					} else {
						if (!isFollowSymlinks() && file.isSymbolicLink()) {
							filesExcluded.addElement(name)
						} else if (isFunctioningAsFile(ftp, dir, file)) {
							accountForIncludedFile(name)
						}
					}
				}
			}
			ftp.changeToParentDirectory()
		} catch (IOException e) {
			throw new GradleException("Error while communicating with FTP server", e)
		}
	}
	/**
	 * process included file
	 * @param name path of the file relative to the directory of the fileset
	 */
	private void accountForIncludedFile(String name) {
		if (!filesIncluded.contains(name) && !filesExcluded.contains(name)) {

			if (isIncluded(name)) {
				if (!isExcluded(name) && isSelected(name, (File) scannedDirs.get(name))) {
					filesIncluded.addElement(name)
				} else {
					filesExcluded.addElement(name)
				}
			} else {
				filesNotIncluded.addElement(name)
			}
		}
	}

	/**
	 *
	 * @param name path of the directory relative to the directory of
	 * the fileset
	 * @param file directory as file
	 * @param fast
	 */
	private void accountForIncludedDir(String name, AntFTPFile file, boolean fast) {
		if (!dirsIncluded.contains(name) && !dirsExcluded.contains(name)) {
			if (!isExcluded(name)) {
				if (fast) {
					if (file.isSymbolicLink()) {
						try {
							file.getClient().changeWorkingDirectory(file.curpwd)
						} catch (IOException ioe) {
							throw new GradleException("could not change directory to curpwd", ioe)
						}
						scandir(file.getLink(), name + File.separator, fast)
					} else {
						try {
							file.getClient().changeWorkingDirectory(file.curpwd)
						} catch (IOException ioe) {
							throw new GradleException("could not change directory to curpwd", ioe)
						}
						scandir(file.getName(), name + File.separator, fast)
					}
				}
				dirsIncluded.addElement(name)
			} else {
				dirsExcluded.addElement(name)
				if (fast && couldHoldIncluded(name)) {
					try {
						file.getClient().changeWorkingDirectory(file.curpwd)
					} catch (IOException ioe) {
						throw new GradleException("could not change directory to curpwd", ioe)
					}
					scandir(file.getName(), name + File.separator, fast)
				}
			}
		}
	}

	/**
	 * temporary table to speed up the various scanning methods below
	 *
	 * @since Ant 1.6
	 */
	private Map fileListMap = new HashMap()
	/**
	 * List of all scanned directories.
	 *
	 * @since Ant 1.6
	 */

	private Map scannedDirs = new HashMap()

	/**
	 * Has the directory with the given path relative to the base
	 * directory already been scanned?
	 *
	 * @since Ant 1.6
	 */
	private boolean hasBeenScanned(String vpath) {
		return scannedDirs.containsKey(vpath)
	}

	/**
	 * Clear internal caches.
	 *
	 * @since Ant 1.6
	 */
	private void clearCaches() {
		fileListMap.clear()
		scannedDirs.clear()
	}
	/**
	 * list the files present in one directory.
	 * @param directory full path on the remote side
	 * @param changedir if true change to directory directory before listing
	 * @return array of FTPFile
	 */
	public FTPFile[] listFiles(String directory, boolean changedir) {
		log.debug("listing files in directory ${directory}")
		String currentPath = directory
		if (changedir) {
			boolean result = ftp.changeWorkingDirectory(directory)
			if (!result) {
				return null
			}
			currentPath = ftp.printWorkingDirectory()
		}
		if (fileListMap.containsKey(currentPath)) {
			log.debug("filelist map used in listing files")
			return ((FTPFile[]) fileListMap.get(currentPath))
		}
		FTPFile[] result = ftp.listFiles()
		fileListMap.put(currentPath, result)
		if (!remoteSensitivityChecked) {
			checkRemoteSensitivity(result, directory)
		}
		return result
	}

	private void forceRemoteSensitivityCheck() {
		if (!remoteSensitivityChecked) {
			checkRemoteSensitivity(ftp.listFiles(), ftp.printWorkingDirectory())
		}
	}

	/**
	 * cd into one directory and
	 * list the files present in one directory.
	 * @param directory full path on the remote side
	 * @return array of FTPFile
	 */
	public FTPFile[] listFiles(String directory) {
		return listFiles(directory, true)
	}

	private void checkRemoteSensitivity(FTPFile[] array, String directory) {
		if (array == null) {
			return
		}
		boolean candidateFound = false
		String target = null
		for (int icounter = 0; icounter < array.length; icounter++) {
			if (array[icounter] != null && array[icounter].isDirectory()) {
				if (!array[icounter].getName().equals(".")
						&& !array[icounter].getName().equals("..")) {
					candidateFound = true
					target = fiddleName(array[icounter].getName())
					log.debug("will try to cd to ${target} where a directory called ${array[icounter].getName()} exists")
					for (int pcounter = 0; pcounter < array.length; pcounter++) {
						if (array[pcounter] != null
								&& pcounter != icounter
								&& target.equals(array[pcounter].getName())) {
							candidateFound = false
							break
						}
					}
					if (candidateFound) {
						break
					}
				}
			}
		}
		if (candidateFound) {
			try {
				log.debug("testing case sensitivity, attempting to cd to ${target}")
				remoteSystemCaseSensitive = !ftp.changeWorkingDirectory(target)
			} catch (IOException ioe) {
				remoteSystemCaseSensitive = true
			} finally {
				ftp.changeWorkingDirectory(directory)
			}
			log.debug("remote system is case sensitive : ${remoteSystemCaseSensitive}")
			remoteSensitivityChecked = true
		}
	}

	private String fiddleName(String origin) {
		StringBuffer result = new StringBuffer()
		for (int icounter = 0; icounter < origin.length(); icounter++) {
			if (Character.isLowerCase(origin.charAt(icounter))) {
				result.append(Character.toUpperCase(origin.charAt(icounter)))
			} else if (Character.isUpperCase(origin.charAt(icounter))) {
				result.append(Character.toLowerCase(origin.charAt(icounter)))
			} else {
				result.append(origin.charAt(icounter))
			}
		}
		return result.toString()
	}

	/**
	 * check FTPFiles to check whether they function as directories too
	 * the FTPFile API seem to make directory and symbolic links incompatible
	 * we want to find out if we can cd to a symbolic link
	 * @param dir the parent directory of the file to test
	 * @param file the file to test
	 * @return true if it is possible to cd to this directory
	 * @since ant 1.6
	 */
	private boolean isFunctioningAsDirectory(FTPClient ftp, String dir, FTPFile file) {
		boolean result = false
		String currentWorkingDir = null
		if (file.isDirectory()) {
			return true
		} else if (file.isFile()) {
			return false
		}
		try {
			currentWorkingDir = ftp.printWorkingDirectory()
		} catch (IOException ioe) {
			log.debug("could not find current working directory ${dir} while checking a symlink", ioe)
		}
		if (currentWorkingDir != null) {
			try {
				result = ftp.changeWorkingDirectory(file.getLink())
			} catch (IOException ioe) {
				log.debug("could not cd to ${file.getLink()} while checking a symlink", ioe)
			}
			if (result) {
				boolean comeback = false
				try {
					comeback = ftp.changeWorkingDirectory(currentWorkingDir)
				} catch (IOException ioe) {
					log.error("could not cd back to ${dir} while checking a symlink", ioe)
				} finally {
					if (!comeback) {
						throw new GradleException("could not cd back to ${dir} while checking a symlink")
					}
				}
			}
		}
		return result
	}
	/**
	 * check FTPFiles to check whether they function as directories too
	 * the FTPFile API seem to make directory and symbolic links incompatible
	 * we want to find out if we can cd to a symbolic link
	 * @param dir the parent directory of the file to test
	 * @param file the file to test
	 * @return true if it is possible to cd to this directory
	 * @since ant 1.6
	 */
	private boolean isFunctioningAsFile(FTPClient ftp, String dir, FTPFile file) {
		if (file.isDirectory()) {
			return false
		} else if (file.isFile()) {
			return true
		}
		return !isFunctioningAsDirectory(ftp, dir, file)
	}

	/**
	 * an AntFTPFile is a representation of a remote file
	 * @since Ant 1.6
	 */
	private class AntFTPFile {
		/**
		 * ftp client
		 */
		private FTPClient client
		/**
		 * parent directory of the file
		 */
		private String curpwd
		/**
		 * the file itself
		 */
		private FTPFile ftpFile
		/**
		 *
		 */
		private AntFTPFile parent = null
		private boolean relativePathCalculated = false
		private boolean traversesSymlinks = false
		private String relativePath = ""
		/**
		 * constructor
		 * @param client ftp client variable
		 * @param ftpFile the file
		 * @param curpwd absolute remote path where the file is found
		 */
		public AntFTPFile(FTPClient client, FTPFile ftpFile, String curpwd) {
			this.client = client
			this.ftpFile = ftpFile
			this.curpwd = curpwd
		}

		/**
		 * other constructor
		 * @param parent the parent file
		 * @param path a relative path to the parent file
		 */
		public AntFTPFile(AntFTPFile parent, String path) {
			this.parent = parent
			this.client = parent.client
			Vector pathElements = SelectorUtils.tokenizePath(path)
			try {
				boolean result = this.client.changeWorkingDirectory(parent.getAbsolutePath())
				//this should not happen, except if parent has been deleted by another process
				if (!result) {
					return
				}
				this.curpwd = parent.getAbsolutePath()
			} catch (IOException ioe) {
				throw new GradleException("could not change working dir to ${parent.curpwd}", ioe)
			}
			final int size = pathElements.size()
			for (int fcount = 0; fcount < size - 1; fcount++) {
				String currentPathElement = (String) pathElements.elementAt(fcount)
				try {
					boolean result = this.client.changeWorkingDirectory(currentPathElement)
					if (!result && !isCaseSensitive()
							&& (remoteSystemCaseSensitive || !remoteSensitivityChecked)) {
						currentPathElement = findPathElementCaseUnsensitive(this.curpwd,
								currentPathElement)
						if (currentPathElement == null) {
							return
						}
					} else if (!result) {
						return
					}
					this.curpwd = getCurpwdPlusFileSep()
					+currentPathElement
				} catch (IOException ioe) {
					String toPath = pathElements.elementAt(fcount)
					throw new GradleException("could not change working dir to ${toPath} from ${curpwd}", ioe)
				}

			}
			String lastpathelement = (String) pathElements.elementAt(size - 1)
			FTPFile[] theFiles = listFiles(this.curpwd)
			this.ftpFile = getFile(theFiles, lastpathelement)
		}
		/**
		 * find a file in a directory in case unsensitive way
		 * @param parentPath where we are
		 * @param soughtPathElement what is being sought
		 * @return the first file found or null if not found
		 */
		private String findPathElementCaseUnsensitive(String parentPath, String soughtPathElement) {
			// we are already in the right path, so the second parameter
			// is false
			FTPFile[] theFiles = listFiles(parentPath, false)
			if (theFiles == null) {
				return null
			}
			for (int icounter = 0; icounter < theFiles.length; icounter++) {
				if (theFiles[icounter] != null
						&& theFiles[icounter].getName().equalsIgnoreCase(soughtPathElement)) {
					return theFiles[icounter].getName()
				}
			}
			return null
		}
		/**
		 * find out if the file exists
		 * @return true if the file exists
		 */
		public boolean exists() {
			return (ftpFile != null)
		}
		/**
		 * if the file is a symbolic link, find out to what it is pointing
		 * @return the target of the symbolic link
		 */
		public String getLink() {
			return ftpFile.getLink()
		}
		/**
		 * get the name of the file
		 * @return the name of the file
		 */
		public String getName() {
			return ftpFile.getName()
		}
		/**
		 * find out the absolute path of the file
		 * @return absolute path as string
		 */
		public String getAbsolutePath() {
			return getCurpwdPlusFileSep() + ftpFile.getName()
		}
		/**
		 * find out the relative path assuming that the path used to construct
		 * this AntFTPFile was spelled properly with regards to case.
		 * This is OK on a case sensitive system such as UNIX
		 * @return relative path
		 */
		public String getFastRelativePath() {
			String absPath = getAbsolutePath()
			if (absPath.startsWith(rootPath + remoteFileSep)) {
				return absPath.substring(rootPath.length() + remoteFileSep.length())
			}
			return null
		}
		/**
		 * find out the relative path to the rootPath of the enclosing scanner.
		 * this relative path is spelled exactly like on disk,
		 * for instance if the AntFTPFile has been instantiated as ALPHA,
		 * but the file is really called alpha, this method will return alpha.
		 * If a symbolic link is encountered, it is followed, but the name of the link
		 * rather than the name of the target is returned.
		 * (ie does not behave like File.getCanonicalPath())
		 * @return relative path, separated by remoteFileSep
		 * @throws IOException    if a change directory fails, ...
		 * @throws GradleException if one of the components of the relative path cannot
		 * be found.
		 */
		public String getRelativePath() throws IOException, GradleException {
			if (!relativePathCalculated) {
				if (parent != null) {
					traversesSymlinks = parent.isTraverseSymlinks()
					relativePath = doGetRelativePath(parent.getAbsolutePath(),
							parent.getRelativePath())
				} else {
					relativePath = doGetRelativePath(rootPath, "")
					relativePathCalculated = true
				}
			}
			return relativePath
		}
		/**
		 * get thge relative path of this file
		 * @param currentPath base path
		 * @param currentRelativePath relative path of the base path with regards to remote dir
		 * @return relative path
		 */
		private String doGetRelativePath(String currentPath, String currentRelativePath) {
			Vector pathElements = SelectorUtils.tokenizePath(getAbsolutePath(), remoteFileSep)
			Vector pathElements2 = SelectorUtils.tokenizePath(currentPath, remoteFileSep)
			String relPath = currentRelativePath
			final int size = pathElements.size()
			for (int pcount = pathElements2.size(); pcount < size; pcount++) {
				String currentElement = (String) pathElements.elementAt(pcount)
				FTPFile[] theFiles = listFiles(currentPath)
				FTPFile theFile = null
				if (theFiles != null) {
					theFile = getFile(theFiles, currentElement)
				}
				if (!relPath.equals("")) {
					relPath = relPath + remoteFileSep
				}
				if (theFile == null) {
					// hit a hidden file assume not a symlink
					relPath = relPath + currentElement
					currentPath = currentPath + remoteFileSep + currentElement
					log.debug("Hidden file ${relPath} assumed to not be a symlink.")
				} else {
					traversesSymlinks = traversesSymlinks || theFile.isSymbolicLink()
					relPath = relPath + theFile.getName()
					currentPath = currentPath + remoteFileSep + theFile.getName()
				}
			}
			return relPath
		}
		/**
		 * find a file matching a string in an array of FTPFile.
		 * This method will find "alpha" when requested for "ALPHA"
		 * if and only if the caseSensitive attribute is set to false.
		 * When caseSensitive is set to true, only the exact match is returned.
		 * @param theFiles array of files
		 * @param lastpathelement the file name being sought
		 * @return null if the file cannot be found, otherwise return the matching file.
		 */
		public FTPFile getFile(FTPFile[] theFiles, String lastpathelement) {
			if (theFiles == null) {
				return null
			}
			for (int fcount = 0; fcount < theFiles.length; fcount++) {
				if (theFiles[fcount] != null) {
					if (theFiles[fcount].getName().equals(lastpathelement)) {
						return theFiles[fcount]
					} else if (!isCaseSensitive()
							&& theFiles[fcount].getName().equalsIgnoreCase(
							lastpathelement)) {
						return theFiles[fcount]
					}
				}
			}
			return null
		}
		/**
		 * tell if a file is a directory.
		 * note that it will return false for symbolic links pointing to directories.
		 * @return <code>true</code> for directories
		 */
		public boolean isDirectory() {
			return ftpFile.isDirectory()
		}
		/**
		 * tell if a file is a symbolic link
		 * @return <code>true</code> for symbolic links
		 */
		public boolean isSymbolicLink() {
			return ftpFile.isSymbolicLink()
		}
		/**
		 * return the attached FTP client object.
		 * Warning : this instance is really shared with the enclosing class.
		 * @return FTP client
		 */
		protected FTPClient getClient() {
			return client
		}

		/**
		 * sets the current path of an AntFTPFile
		 * @param curpwd the current path one wants to set
		 */
		protected void setCurpwd(String curpwd) {
			this.curpwd = curpwd
		}
		/**
		 * returns the path of the directory containing the AntFTPFile.
		 * of the full path of the file itself in case of AntFTPRootFile
		 * @return parent directory of the AntFTPFile
		 */
		public String getCurpwd() {
			return curpwd
		}
		/**
		 * returns the path of the directory containing the AntFTPFile.
		 * of the full path of the file itself in case of AntFTPRootFile
		 * and appends the remote file separator if necessary.
		 * @return parent directory of the AntFTPFile
		 * @since Ant 1.8.2
		 */
		public String getCurpwdPlusFileSep() {
			return curpwd.endsWith(remoteFileSep) ? curpwd
					: curpwd + remoteFileSep
		}
		/**
		 * find out if a symbolic link is encountered in the relative path of this file
		 * from rootPath.
		 * @return <code>true</code> if a symbolic link is encountered in the relative path.
		 * @throws IOException if one of the change directory or directory listing operations
		 * fails
		 * @throws GradleException if a path component in the relative path cannot be found.
		 */
		public boolean isTraverseSymlinks() throws IOException, GradleException {
			if (!relativePathCalculated) {
				// getRelativePath also finds about symlinks
				getRelativePath()
			}
			return traversesSymlinks
		}

		/**
		 * Get a string rep of this object.
		 * @return a string containing the pwd and the file.
		 */
		public String toString() {
			return "AntFtpFile: " + curpwd + "%" + ftpFile
		}
	}
	/**
	 * special class to represent the remote directory itself
	 * @since Ant 1.6
	 */
	private class AntFTPRootFile extends AntFTPFile {

		public AntFTPRootFile(FTPClient client, String remotedir) {
			super(client, null, remotedir)
			setCurpwd(client.printWorkingDirectory())
		}

		public String getAbsolutePath() {
			return this.getCurpwd()
		}

		public String getRelativePath() {
			return ""
		}
	}

	/**
	 * internal class providing a File-like interface to some of the information
	 * available from the FTP server
	 */
	private class FTPFileProxy extends File {

		private final FTPFile file
		private final String[] parts
		private final String name

		/**
		 * creates a proxy to a FTP file
		 * @param file
		 */
		public FTPFileProxy(FTPFile file) {
			super(file.getName())
			name = file.getName()
			this.file = file
			parts = FileUtils.getPathStack(name)
		}

		/**
		 * creates a proxy to a FTP directory
		 * @param completePath the remote directory.
		 */
		public FTPFileProxy(String completePath) {
			super(completePath)
			file = null
			name = completePath
			parts = FileUtils.getPathStack(completePath)
		}

		public boolean exists() {
			return true
		}

		public String getAbsolutePath() {
			return name
		}

		public String getName() {
			return parts.length > 0 ? parts[parts.length - 1] : name
		}

		public String getParent() {
			String result = ""
			for (int i = 0; i < parts.length - 1; i++) {
				result += "${separatorChar}${parts[i]}"
			}
			return result
		}

		public String getPath() {
			return name
		}

		/**
		 * FTP files are stored as absolute paths
		 * @return true
		 */
		public boolean isAbsolute() {
			return true
		}

		public boolean isDirectory() {
			return file == null
		}

		public boolean isFile() {
			return file != null
		}

		/**
		 * FTP files cannot be hidden
		 *
		 * @return false
		 */
		public boolean isHidden() {
			return false
		}

		public long lastModified() {
			if (file != null) {
				return file.getTimestamp().getTimeInMillis()
			}
			return 0
		}

		public long length() {
			if (file != null) {
				return file.getSize()
			}
			return 0
		}
	}

}
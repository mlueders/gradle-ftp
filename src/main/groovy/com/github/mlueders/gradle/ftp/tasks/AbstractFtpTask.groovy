package com.github.mlueders.gradle.ftp.tasks

import com.github.mlueders.gradle.ftp.FtpAdapter
import com.github.mlueders.gradle.ftp.RetryHandler
import groovy.io.FileType
import groovy.transform.EqualsAndHashCode
import groovy.util.logging.Slf4j
import org.apache.tools.ant.DirectoryScanner
import org.apache.tools.ant.types.FileSet
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.tooling.BuildException

@Slf4j
abstract class AbstractFtpTask extends DefaultTask {

	@EqualsAndHashCode
	static class TransferableFile {
		String baseDir
		String relativePath
	}

	static class FtpFileProcessor {

		boolean remoteScanner = true
		boolean baseDirRequired = false
		String actionString
		String targetString
		String completedString

		private Project project
		private FtpAdapter ftpAdapter
		private RetryHandler retryHandler
		private List<FileSet> filesets = []
		private String remoteDir

		FtpFileProcessor(Project project, FtpAdapter ftpAdapter, RetryHandler retryHandler, List<FileSet> filesets, String remoteDir) {
			this.project = project
			this.ftpAdapter = ftpAdapter
			this.retryHandler = retryHandler
			this.filesets = filesets
			this.remoteDir = remoteDir
		}

		void transferFilesWithRetry(Closure action) {
			transferFilesWithRetry(getFilesToTransfer(), action)
		}

		void transferFilesWithRetry(List<TransferableFile> filesToTransfer, Closure action) {
			log.info("${actionString} ${targetString}")

			if (filesToTransfer.isEmpty()) {
				log.info("Empty fileset, nothing to do")
				return
			}

			if (remoteDir != null) {
				ftpAdapter.changeWorkingDirectory(remoteDir)
			}

			for (TransferableFile file : filesToTransfer) {
				retryHandler.execute(file.relativePath) {
					action.call(file)
				}
			}

			log.info("${ftpAdapter.transferred} ${targetString} ${completedString}")
			if (ftpAdapter.skipped != 0) {
				log.info("${ftpAdapter.skipped} ${targetString} were not successfully ${completedString}")
			}
		}

		List<TransferableFile> getFilesToTransfer() {
			getFilesOfTypeToTransfer(FileType.FILES)
		}

		List<TransferableFile> getDirectoriesToTransfer() {
			getFilesOfTypeToTransfer(FileType.DIRECTORIES)
		}

		private List<TransferableFile> getFilesOfTypeToTransfer(FileType fileType) {
			if (filesets.size() == 0) {
				throw new GradleException("at least one fileset must be specified.");
			}

			List<TransferableFile> filesToTransfer = []
			for (FileSet fs : filesets) {
				filesToTransfer.addAll(getFilesOfTypeToTransfer(fs, fileType))
			}
			filesToTransfer.unique()
		}

		private List<TransferableFile> getFilesOfTypeToTransfer(FileSet fileset, FileType fileType) {
			if (baseDirRequired && (fileset.getDir() == null)) {
				throw new GradleException("the dir attribute is required to be set on the fileset")
			}

			DirectoryScanner ds = createDirectoryScannerForFileSet(fileset)
			String baseDir = ds.getBasedir()

			String[] dsfiles = (fileType == FileType.DIRECTORIES) ? ds.getIncludedDirectories() : ds.getIncludedFiles()
			dsfiles.collect { String relativePath ->
				new TransferableFile(baseDir: baseDir, relativePath: relativePath)
			}
		}

		private DirectoryScanner createDirectoryScannerForFileSet(FileSet fileset) {
			DirectoryScanner ds
			if (remoteScanner) {
				ds = ftpAdapter.getRemoteDirectoryScanner(remoteDir)
				fileset.setupDirectoryScanner(ds, project.ant.project)
				ds.setFollowSymlinks(fileset.isFollowSymlinks())
				ds.scan()
			} else {
				ds = fileset.getDirectoryScanner(project.ant.project)
			}
			ds
		}
	}

	/**
	 * The remote directory where files will be placed. This may be a
	 * relative or absolute path, and must be in the path syntax expected by
	 * the remote server. No correction of path syntax will be performed.
	 */
	String remoteDir
	/**
	 * Defines how many times to retry executing FTP command before giving up.
	 * A negative value means keep trying forever.
	 * Default is 0 - try once and give up if failure.
	 */
    int retriesAllowed = 0

	@Delegate
	public FtpAdapter.Config config = new FtpAdapter.Config()
	private FtpAdapter ftpAdapter
	private RetryHandler retryHandler
	private FtpFileProcessor ftpFileProcessor
	private List<FileSet> filesets = []

	/**
	 * Checks to see that all required parameters are set.
	 *
	 * @throws GradleException if the configuration is not valid.
	 */
	protected void checkAttributes() {}

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
	 * Runs the task.
	 *
	 * @throws GradleException if the task fails or is not configured correctly.
	 */
	@TaskAction
	public void executeFtpTask() throws GradleException {
		checkAttributes()
		ftpAdapter = new FtpAdapter(config)
		retryHandler = new RetryHandler(retriesAllowed, log)
		ftpFileProcessor = new FtpFileProcessor(project, ftpAdapter, retryHandler, filesets, remoteDir)

		try {
			ftpAdapter.open(retryHandler)
			onExecuteFtpTask(ftpFileProcessor, ftpAdapter, retryHandler)
		} catch (IOException ex) {
			throw new GradleException("error during FTP transfer: ${ex}", ex)
		} finally {
			ftpAdapter.close()
		}
	}

	protected abstract void onExecuteFtpTask(FtpFileProcessor processor, FtpAdapter ftpAdapter, RetryHandler retryHandler)

}

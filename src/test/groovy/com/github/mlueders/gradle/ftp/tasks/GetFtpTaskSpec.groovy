package com.github.mlueders.gradle.ftp.tasks

import com.bancvue.gradle.test.TestFile
import org.gradle.api.GradleException
import org.joda.time.DateTime
import spock.lang.Unroll

class GetFtpTaskSpec extends AbstractFtpTaskSpec<GetFtpTask> {
	
	def setup() {
		addFtpFile(ftpBaseDir.file('base-file'), 'base-file contents')
	}

	@Override
	protected Class<AbstractFtpTask> getTaskType() {
		GetFtpTask
	}

	def "should get file"() {
		given:
		TestFile targetDir = projectFS.mkdir('target-dir')
		ftpTask.fileset(targetDir.name) {
			include(name: '**')
		}

		when:
		ftpTask.executeFtpTask()

		then:
		TestFile baseFile = targetDir.file('base-file')
		assert baseFile.exists()
		assert baseFile.text == 'base-file contents'
	}

	def "should auto create target directory when landing files"() {
		given:
		TestFile targetDir = projectFS.file('target-dir')
		ftpTask.fileset(targetDir.name) {
			include(name: '**')
		}
		assert !targetDir.exists()

		when:
		ftpTask.executeFtpTask()

		then:
		assert targetDir.exists()
		assert targetDir.file('base-file').exists()
	}

	def "should get files from multiple filesets and place in directory associated with the fileset"() {
		given:
		addFtpFile(ftpBaseDir.file('primary-file'))
		addFtpFile(ftpBaseDir.file('secondary-file'))
		TestFile primaryDir = projectFS.mkdir('primary-dir')
		TestFile secondaryDir = projectFS.mkdir('secondary-dir')
		ftpTask.fileset(primaryDir.name) {
			include(name: 'primary-file')
		}
		ftpTask.fileset(secondaryDir.name) {
			include(name: 'secondary-file')
		}

		when:
		ftpTask.executeFtpTask()

		then:
		assert primaryDir.listFiles() == [new File(primaryDir, 'primary-file')]
		assert secondaryDir.listFiles() == [new File(secondaryDir, 'secondary-file')]
	}

	def "should fail if dir not set on fileset"() {
		given:
		ftpTask.fileset {
			include(name: '**')
		}

		when:
		ftpTask.executeFtpTask()

		then:
		thrown(GradleException)
	}

	@Unroll
	def "should set lastModified of local file to #description if preserveLastModified is #preserveLastModified"() {
		given:
		TestFile targetDir = projectFS.mkdir('target-dir')
		ftpTask.preserveLastModified = preserveLastModified
		ftpTask.fileset(targetDir.name) {
			include(name: '**')
		}

		when:
		ftpTask.executeFtpTask()

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
		ftpTask.fileset(targetDir.name) {
			include(name: 'file.txt')
		}

		when:
		ftpTask.executeFtpTask()

		then:
		assert localFile.text == expectedContent

		where:
		newerOnly | shouldOverwrite | shouldOverwriteString | expectedContent
		false     | true            | 'overwrite'           | 'out-of-date contents'
		true      | false           | 'not overwrite'       | 'up-to-date contents'
	}

}

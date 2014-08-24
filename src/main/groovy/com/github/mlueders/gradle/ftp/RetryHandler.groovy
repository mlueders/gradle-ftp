package com.github.mlueders.gradle.ftp

import org.slf4j.Logger

class RetryHandler {

	private int retriesAllowed
	private Logger defaultLogger

	RetryHandler(int retriesAllowed, Logger defaultLogger) {
		this.retriesAllowed = retriesAllowed
		this.defaultLogger = defaultLogger
	}

	void execute(String desc, Logger logger = defaultLogger, Closure retryable) throws IOException {
		int retries = 0;
		while (true) {
			try {
				retryable.call()
				break;
			} catch (IOException e) {
				retries++;

				if (retries > this.retriesAllowed && this.retriesAllowed > -1) {
					logger.warn("try #${retries}: IO error (${desc}), number of maximum retries reached " +
							"(${retriesAllowed}), giving up")
					throw e;
				} else {
					logger.warn("try #${retries}: IO error (${desc}), retrying")
				}
			}
		}
	}

}

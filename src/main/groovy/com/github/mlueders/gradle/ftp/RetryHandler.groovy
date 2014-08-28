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

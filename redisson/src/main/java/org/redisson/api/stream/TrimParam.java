/**
 * Copyright (c) 2021 Ivano Pagano
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.api.stream;

import org.redisson.api.StreamMessageId;

/**
 * Use the static methods to build a parameter used in stream trimming.
 * Different {@link TrimStrategy} require different types of TrimParam.
 * Use the constructor docs as guidance.
 */
public abstract class TrimParam {
	/* We use a design pattern to enforce usage of "smart constructors", i.e. factory methods.
	 * Constructors visibility is made private, so client code has limited choice.
	 */

	/* Limit access */
	private TrimParam() {
	}

	/* Utility */
	private static TrimParam fromValue(Object value) {
		return new TrimParam() {
			@Override
			public Object getValue() {
				return value;
			}
		};
	}

	/**
	 * Provides a value used to support the trimming command
	 * @return a generic parameter
	 */
	public abstract Object getValue();

	/**
	 * Creates a param compatible with the {@link TrimStrategy.MAXLEN}.
	 */
	public static TrimParam maxLen(int max) {
		return fromValue(Integer.valueOf(max));
	}

	/**
	 * Creates a param compatible with the {@link TrimStrategy.MINID}.
	 * The id will be fromValue exactly matching the passed-in StreamMessageId
	 */
	public static TrimParam minId(StreamMessageId id) {
		return fromValue(String.join("-", String.valueOf(id.getId0()), String.valueOf(id.getId1())));
	}

	/**
	 * Creates a param compatible with the {@link TrimStrategy.MINID}.
	 * The id will be matched partially using the passed-in string.
	 */
	public static TrimParam minId(String idMatcher) {
		return fromValue(idMatcher);
	}


}
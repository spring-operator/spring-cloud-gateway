/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.springframework.cloud.gateway.filter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.gateway.filter.RemoveHopByHopHeadersFilter.HEADERS_REMOVED_ON_REQUEST;

/**
 * @author Spencer Gibb
 */
public class RemoveHopByHopHeadersFilterTests {

	@Test
	public void happyPath() {
		MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest
				.get("http://localhost/get");

		HEADERS_REMOVED_ON_REQUEST.forEach(header -> builder.header(header, header+"1"));

		testFilter(builder.build());
	}

	@Test
	public void caseInsensitive() {
		MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest
				.get("http://localhost/get");

		HEADERS_REMOVED_ON_REQUEST.forEach(header -> builder.header(header.toLowerCase(), header+"1"));

		testFilter(builder.build());
	}

	@Test
	public void removesHeadersListedInConnectionHeader() {
		MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest
				.get("http://localhost/get");

		builder.header(HttpHeaders.CONNECTION, "upgrade", "keep-alive");
		builder.header(HttpHeaders.UPGRADE, "WebSocket");
		builder.header("Keep-Alive", "timeout:5");

		testFilter(builder.build(), "upgrade", "keep-alive");
	}

	private void testFilter(MockServerHttpRequest request, String... additionalHeaders) {
		RemoveHopByHopHeadersFilter filter = new RemoveHopByHopHeadersFilter();
		HttpHeaders headers = filter.filter(request.getHeaders());

		Set<String> toRemove = new HashSet<>(HEADERS_REMOVED_ON_REQUEST);
		toRemove.addAll(Arrays.asList(additionalHeaders));
		assertThat(headers).doesNotContainKeys(toRemove.toArray(new String[0]));
	}
}

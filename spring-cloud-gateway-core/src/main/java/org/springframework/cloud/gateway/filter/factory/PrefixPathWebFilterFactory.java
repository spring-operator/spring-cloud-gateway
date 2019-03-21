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

package org.springframework.cloud.gateway.filter.factory;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.tuple.Tuple;
import org.springframework.web.server.WebFilter;

import java.util.Arrays;
import java.util.List;

/**
 * @author Spencer Gibb
 */
public class PrefixPathWebFilterFactory implements WebFilterFactory {

	public static final String PREFIX_KEY = "prefix";

	@Override
	public List<String> argNames() {
		return Arrays.asList(PREFIX_KEY);
	}

	@Override
	@SuppressWarnings("unchecked")
	public WebFilter apply(Tuple args) {
		final String prefix = args.getString(PREFIX_KEY);

		return (exchange, chain) -> {
			ServerHttpRequest req = exchange.getRequest();
			String newPath = prefix + req.getURI().getPath();

			ServerHttpRequest request = req.mutate()
					.path(newPath)
					.build();

			return chain.filter(exchange.mutate().request(request).build());
		};
	}
}

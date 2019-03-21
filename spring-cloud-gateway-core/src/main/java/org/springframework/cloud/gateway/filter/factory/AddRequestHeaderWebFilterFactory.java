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

import org.springframework.tuple.Tuple;
import org.springframework.web.server.WebFilter;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.util.Arrays;
import java.util.List;

/**
 * @author Spencer Gibb
 */
public class AddRequestHeaderWebFilterFactory implements WebFilterFactory {

	@Override
	public List<String> argNames() {
		return Arrays.asList(NAME_KEY, VALUE_KEY);
	}

	@Override
	public WebFilter apply(Tuple args) {
		String name = args.getString(NAME_KEY);
		String value = args.getString(VALUE_KEY);

		return (exchange, chain) -> {
			ServerHttpRequest request = exchange.getRequest().mutate()
					.header(name, value)
					.build();

			return chain.filter(exchange.mutate().request(request).build());
		};
	}
}

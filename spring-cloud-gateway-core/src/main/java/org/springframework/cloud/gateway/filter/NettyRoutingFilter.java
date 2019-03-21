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

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR;
import static org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_REQUEST_URL_ATTR;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import reactor.core.publisher.Mono;
import reactor.ipc.netty.NettyPipeline;
import reactor.ipc.netty.http.client.HttpClient;
import reactor.ipc.netty.http.client.HttpClientRequest;

/**
 * @author Spencer Gibb
 */
public class NettyRoutingFilter implements GlobalFilter, Ordered {

	//TODO: investigate using WebClient
	//The WebSocketRoutingFilter worked out so well without a hard dependency on netty
	private final HttpClient httpClient;

	public NettyRoutingFilter(HttpClient httpClient) {
		this.httpClient = httpClient;
	}

	@Override
	public int getOrder() {
		return 2000000;
	}

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		URI requestUrl = exchange.getRequiredAttribute(GATEWAY_REQUEST_URL_ATTR);

		String scheme = requestUrl.getScheme();
		if (!scheme.equals("http") && !scheme.equals("https")) {
			return chain.filter(exchange);
		}

		ServerHttpRequest request = exchange.getRequest();

		final HttpMethod method = HttpMethod.valueOf(request.getMethod().toString());
		final String url = requestUrl.toString();

		final DefaultHttpHeaders httpHeaders = new DefaultHttpHeaders();
		request.getHeaders().forEach(httpHeaders::set);

		return this.httpClient.request(method, url, req -> {
			final HttpClientRequest proxyRequest = req.options(NettyPipeline.SendOptions::flushOnEach)
					.failOnClientError(false)
					.headers(httpHeaders);

			if (MediaType.APPLICATION_FORM_URLENCODED.includes(request.getHeaders().getContentType())) {
				return exchange.getFormData()
						.flatMap(map -> proxyRequest.sendForm(form -> {
							for (Map.Entry<String, List<String>> entry: map.entrySet()) {
								for (String value : entry.getValue()) {
									form.attr(entry.getKey(), value);
								}
							}
						}).then())
						.then(chain.filter(exchange));
			}

			return proxyRequest.sendHeaders() //I shouldn't need this
					.send(request.getBody()
							.map(DataBuffer::asByteBuffer)
							.map(Unpooled::wrappedBuffer));
		}).doOnNext(res -> {
			ServerHttpResponse response = exchange.getResponse();
			// put headers and status so filters can modify the response
			HttpHeaders headers = new HttpHeaders();
			res.responseHeaders().forEach(entry -> headers.add(entry.getKey(), entry.getValue()));

			response.getHeaders().putAll(headers);
			response.setStatusCode(HttpStatus.valueOf(res.status().code()));

			// Defer committing the response until all route filters have run
			// Put client response as ServerWebExchange attribute and write response later NettyWriteResponseFilter
			exchange.getAttributes().put(CLIENT_RESPONSE_ATTR, res);
		}).then(chain.filter(exchange));
	}
}

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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.test.BaseWebClientTests;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.gateway.test.TestUtils.getMap;
import static org.springframework.web.reactive.function.BodyExtractors.toMono;


/**
 * @author Spencer Gibb
 * @author Biju Kunjummen
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@DirtiesContext
@ActiveProfiles(profiles = "request-header-web-filter")
public class AddRequestHeaderGatewayFilterFactoryTests extends BaseWebClientTests {

	@Test
	public void addRequestHeaderFilterWorks() {
		Mono<Map> result = webClient.get()
				.uri("/headers")
				.header("Host", "www.addrequestheader.org")
				.exchange()
				.flatMap(response -> response.body(toMono(Map.class)));

		StepVerifier.create(result)
				.consumeNextWith(
						response -> {
							Map<String, Object> headers = getMap(response, "headers");
							assertThat(headers).containsEntry("X-Request-Foo", "Bar");
						})
				.expectComplete()
				.verify(Duration.ofMinutes(10));
	}

	@EnableAutoConfiguration
	@SpringBootConfiguration
	@Import(DefaultTestConfig.class)
	public static class TestConfig { }

}

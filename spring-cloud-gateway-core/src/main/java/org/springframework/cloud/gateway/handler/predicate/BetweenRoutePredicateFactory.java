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

package org.springframework.cloud.gateway.handler.predicate;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.function.Predicate;

import org.springframework.tuple.Tuple;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;

/**
 * @author Spencer Gibb
 */
public class BetweenRoutePredicateFactory implements RoutePredicateFactory {

	public static final String DATETIME1_KEY = "datetime1";
	public static final String DATETIME2_KEY = "datetime2";

	@Override
	public Predicate<ServerWebExchange> apply(Tuple args) {
		//TODO: is ZonedDateTime the right thing to use?
		final ZonedDateTime dateTime1 = getZonedDateTime(args.getValue(DATETIME1_KEY));
		final ZonedDateTime dateTime2 = getZonedDateTime(args.getValue(DATETIME2_KEY));
		return apply(dateTime1, dateTime2);
	}

	public Predicate<ServerWebExchange> apply(ZonedDateTime dateTime1, ZonedDateTime dateTime2) {
		Assert.isTrue(dateTime1.isBefore(dateTime2), dateTime1 +
				" must be before " + dateTime2);

		return exchange -> {
			final ZonedDateTime now = ZonedDateTime.now();
			return now.isAfter(dateTime1) && now.isBefore(dateTime2);
		};
	}

	public static ZonedDateTime getZonedDateTime(Object value) {
		ZonedDateTime dateTime;
		if (value instanceof ZonedDateTime) {
			dateTime = ZonedDateTime.class.cast(value);
		} else {
			dateTime = parseZonedDateTime(value.toString());
		}
		return dateTime;
	}

	public static ZonedDateTime parseZonedDateTime(String dateString) {
		ZonedDateTime dateTime;
		try {
			long epoch = Long.parseLong(dateString);

			dateTime = Instant.ofEpochMilli(epoch).atOffset(ZoneOffset.ofTotalSeconds(0))
					.toZonedDateTime();
		} catch (NumberFormatException e) {
			// try ZonedDateTime instead
			dateTime = ZonedDateTime.parse(dateString);
		}

		return dateTime;
	}

}

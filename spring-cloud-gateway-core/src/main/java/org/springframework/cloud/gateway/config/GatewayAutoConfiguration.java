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

package org.springframework.cloud.gateway.config;

import java.util.List;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.web.reactive.HttpHandlerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.actuate.GatewayEndpoint;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.NettyRoutingFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.cloud.gateway.filter.RouteToRequestUrlFilter;
import org.springframework.cloud.gateway.filter.WebsocketRoutingFilter;
import org.springframework.cloud.gateway.filter.factory.AddRequestHeaderWebFilterFactory;
import org.springframework.cloud.gateway.filter.factory.AddRequestParameterWebFilterFactory;
import org.springframework.cloud.gateway.filter.factory.AddResponseHeaderWebFilterFactory;
import org.springframework.cloud.gateway.filter.factory.HystrixWebFilterFactory;
import org.springframework.cloud.gateway.filter.factory.PrefixPathWebFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RedirectToWebFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RemoveNonProxyHeadersWebFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RemoveRequestHeaderWebFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RemoveResponseHeaderWebFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RequestRateLimiterWebFilterFactory;
import org.springframework.cloud.gateway.filter.factory.RewritePathWebFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SecureHeadersProperties;
import org.springframework.cloud.gateway.filter.factory.SecureHeadersWebFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SetPathWebFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SetResponseHeaderWebFilterFactory;
import org.springframework.cloud.gateway.filter.factory.SetStatusWebFilterFactory;
import org.springframework.cloud.gateway.filter.factory.WebFilterFactory;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.handler.FilteringWebHandler;
import org.springframework.cloud.gateway.handler.RoutePredicateHandlerMapping;
import org.springframework.cloud.gateway.handler.predicate.AfterRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.BeforeRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.BetweenRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.CookieRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.HeaderRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.HostRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.MethodRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.PathRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.QueryRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.RemoteAddrRoutePredicateFactory;
import org.springframework.cloud.gateway.handler.predicate.RoutePredicateFactory;
import org.springframework.cloud.gateway.route.CachingRouteLocator;
import org.springframework.cloud.gateway.route.CompositeRouteDefinitionLocator;
import org.springframework.cloud.gateway.route.CompositeRouteLocator;
import org.springframework.cloud.gateway.route.InMemoryRouteDefinitionRepository;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;
import org.springframework.cloud.gateway.route.RouteDefinitionRepository;
import org.springframework.cloud.gateway.route.RouteDefinitionRouteLocator;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import org.springframework.web.reactive.socket.server.WebSocketService;
import org.springframework.web.reactive.socket.server.support.HandshakeWebSocketService;

import com.netflix.hystrix.HystrixObservableCommand;

import reactor.core.publisher.Flux;
import reactor.ipc.netty.http.client.HttpClient;
import reactor.ipc.netty.http.client.HttpClientOptions;
import reactor.ipc.netty.resources.PoolResources;
import rx.RxReactiveStreams;

/**
 * @author Spencer Gibb
 */
@Configuration
@ConditionalOnProperty(name = "spring.cloud.gateway.enabled", matchIfMissing = true)
@EnableConfigurationProperties
@AutoConfigureBefore(HttpHandlerAutoConfiguration.class)
@AutoConfigureAfter(GatewayLoadBalancerClientAutoConfiguration.class)
public class GatewayAutoConfiguration {

	@Configuration
	@ConditionalOnClass(HttpClient.class)
	protected static class NettyConfiguration {
		@Bean
		@ConditionalOnMissingBean
		public HttpClient httpClient(@Qualifier("nettyClientOptions") Consumer<? super HttpClientOptions.Builder> options) {
			return HttpClient.create(options);
		}

		@Bean
		public Consumer<? super HttpClientOptions.Builder> nettyClientOptions() {
			return opts -> {
				opts.poolResources(PoolResources.elastic("proxy"));
				// opts.disablePool(); //TODO: why do I need this again?
			};
		}

		@Bean
		public NettyRoutingFilter routingFilter(HttpClient httpClient) {
			return new NettyRoutingFilter(httpClient);
		}

		@Bean
		public NettyWriteResponseFilter nettyWriteResponseFilter() {
			return new NettyWriteResponseFilter();
		}

		@Bean
		public ReactorNettyWebSocketClient reactorNettyWebSocketClient(@Qualifier("nettyClientOptions") Consumer<? super HttpClientOptions.Builder> options) {
			return new ReactorNettyWebSocketClient(options);
		}
	}

	@Bean
	public PropertiesRouteDefinitionLocator propertiesRouteDefinitionLocator(GatewayProperties properties) {
		return new PropertiesRouteDefinitionLocator(properties);
	}

	@Bean
	@ConditionalOnMissingBean(RouteDefinitionRepository.class)
	public InMemoryRouteDefinitionRepository inMemoryRouteDefinitionRepository() {
		return new InMemoryRouteDefinitionRepository();
	}

	@Bean
	@Primary
	public RouteDefinitionLocator routeDefinitionLocator(List<RouteDefinitionLocator> routeDefinitionLocators) {
		return new CompositeRouteDefinitionLocator(Flux.fromIterable(routeDefinitionLocators));
	}

	@Bean
	public RouteLocator routeDefinitionRouteLocator(GatewayProperties properties,
												   List<WebFilterFactory> webFilterFactories,
												   List<RoutePredicateFactory> predicates,
												   RouteDefinitionLocator routeDefinitionLocator) {
		return new RouteDefinitionRouteLocator(routeDefinitionLocator, predicates, webFilterFactories, properties);
	}

	@Bean
	@Primary
	public RouteLocator routeLocator(List<RouteLocator> routeLocators) {
		return new CachingRouteLocator(new CompositeRouteLocator(Flux.fromIterable(routeLocators)));
	}

	@Bean
	public FilteringWebHandler filteringWebHandler(List<GlobalFilter> globalFilters) {
		return new FilteringWebHandler(globalFilters);
	}

	@Bean
	public RoutePredicateHandlerMapping routePredicateHandlerMapping(FilteringWebHandler webHandler,
																	   RouteLocator routeLocator) {
		return new RoutePredicateHandlerMapping(webHandler, routeLocator);
	}

	// ConfigurationProperty beans

	@Bean
	public GatewayProperties gatewayProperties() {
		return new GatewayProperties();
	}

	@Bean
	public SecureHeadersProperties secureHeadersProperties() {
		return new SecureHeadersProperties();
	}

	// GlobalFilter beans

	@Bean
	public RouteToRequestUrlFilter routeToRequestUrlFilter() {
		return new RouteToRequestUrlFilter();
	}

	@Bean
	public WebSocketService webSocketService() {
		return new HandshakeWebSocketService();
	}

	@Bean
	public WebsocketRoutingFilter websocketRoutingFilter(WebSocketClient webSocketClient, WebSocketService webSocketService) {
		return new WebsocketRoutingFilter(webSocketClient, webSocketService);
	}

	/*@Bean
	//TODO: default over netty? configurable
	public WebClientHttpRoutingFilter webClientHttpRoutingFilter() {
		//TODO: WebClient bean
		return new WebClientHttpRoutingFilter(WebClient.builder().build());
	}

	@Bean
	public WebClientWriteResponseFilter webClientWriteResponseFilter() {
		return new WebClientWriteResponseFilter();
	}*/

	// Predicate Factory beans

	@Bean
	public AfterRoutePredicateFactory afterRoutePredicateFactory() {
		return new AfterRoutePredicateFactory();
	}

	@Bean
	public BeforeRoutePredicateFactory beforeRoutePredicateFactory() {
		return new BeforeRoutePredicateFactory();
	}

	@Bean
	public BetweenRoutePredicateFactory betweenRoutePredicateFactory() {
		return new BetweenRoutePredicateFactory();
	}

	@Bean
	public CookieRoutePredicateFactory cookieRoutePredicateFactory() {
		return new CookieRoutePredicateFactory();
	}

	@Bean
	public HeaderRoutePredicateFactory headerRoutePredicateFactory() {
		return new HeaderRoutePredicateFactory();
	}

	@Bean
	public HostRoutePredicateFactory hostRoutePredicateFactory() {
		return new HostRoutePredicateFactory();
	}

	@Bean
	public MethodRoutePredicateFactory methodRoutePredicateFactory() {
		return new MethodRoutePredicateFactory();
	}

	@Bean
	public PathRoutePredicateFactory pathRoutePredicateFactory() {
		return new PathRoutePredicateFactory();
	}

	@Bean
	public QueryRoutePredicateFactory queryRoutePredicateFactory() {
		return new QueryRoutePredicateFactory();
	}

	@Bean
	public RemoteAddrRoutePredicateFactory remoteAddrRoutePredicateFactory() {
		return new RemoteAddrRoutePredicateFactory();
	}

	// WebFilter Factory beans

	@Bean
	public AddRequestHeaderWebFilterFactory addRequestHeaderWebFilterFactory() {
		return new AddRequestHeaderWebFilterFactory();
	}

	@Bean
	public AddRequestParameterWebFilterFactory addRequestParameterWebFilterFactory() {
		return new AddRequestParameterWebFilterFactory();
	}

	@Bean
	public AddResponseHeaderWebFilterFactory addResponseHeaderWebFilterFactory() {
		return new AddResponseHeaderWebFilterFactory();
	}

	@Configuration
	@ConditionalOnClass({HystrixObservableCommand.class, RxReactiveStreams.class})
	protected static class HystrixConfiguration {
		@Bean
		public HystrixWebFilterFactory hystrixWebFilterFactory() {
			return new HystrixWebFilterFactory();
		}
	}

	@Bean
	public PrefixPathWebFilterFactory prefixPathWebFilterFactory() {
		return new PrefixPathWebFilterFactory();
	}

	@Bean
	public RedirectToWebFilterFactory redirectToWebFilterFactory() {
		return new RedirectToWebFilterFactory();
	}

	@Bean
	public RemoveNonProxyHeadersWebFilterFactory removeNonProxyHeadersWebFilterFactory() {
		return new RemoveNonProxyHeadersWebFilterFactory();
	}

	@Bean
	public RemoveRequestHeaderWebFilterFactory removeRequestHeaderWebFilterFactory() {
		return new RemoveRequestHeaderWebFilterFactory();
	}

	@Bean
	public RemoveResponseHeaderWebFilterFactory removeResponseHeaderWebFilterFactory() {
		return new RemoveResponseHeaderWebFilterFactory();
	}

	@Bean
	@ConditionalOnBean({RateLimiter.class, KeyResolver.class})
	public RequestRateLimiterWebFilterFactory requestRateLimiterWebFilterFactory(RateLimiter rateLimiter) {
		return new RequestRateLimiterWebFilterFactory(rateLimiter);
	}

	@Bean
	public RewritePathWebFilterFactory rewritePathWebFilterFactory() {
		return new RewritePathWebFilterFactory();
	}

	@Bean
	public SetPathWebFilterFactory setPathWebFilterFactory() {
		return new SetPathWebFilterFactory();
	}

	@Bean
	public SecureHeadersWebFilterFactory secureHeadersWebFilterFactory(SecureHeadersProperties properties) {
		return new SecureHeadersWebFilterFactory(properties);
	}

	@Bean
	public SetResponseHeaderWebFilterFactory setResponseHeaderWebFilterFactory() {
		return new SetResponseHeaderWebFilterFactory();
	}

	@Bean
	public SetStatusWebFilterFactory setStatusWebFilterFactory() {
		return new SetStatusWebFilterFactory();
	}


	@Configuration
	@ConditionalOnClass(Health.class)
	protected static class GatewayActuatorConfiguration {

		@Bean
		public GatewayEndpoint gatewayEndpoint(RouteDefinitionLocator routeDefinitionLocator, List<GlobalFilter> globalFilters,
											   List<WebFilterFactory> webFilterFactories, RouteDefinitionWriter routeDefinitionWriter,
											   RouteLocator routeLocator) {
			return new GatewayEndpoint(routeDefinitionLocator, globalFilters, webFilterFactories, routeDefinitionWriter, routeLocator);
		}
	}

}


package org.jabref.logic.net.provider;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Predicate;

import org.jabref.logic.importer.FetcherException;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.URIBuilder;
import org.jspecify.annotations.NullMarked;

/// Helper/Builder class for configuring the concrete policies
@NullMarked
final class ProviderSupport {

    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    private ProviderSupport() {
    }

    static RateLimiter rateLimiter(String name, int requests, Duration period, Duration maxWait) {
        return RateLimiter.of(name, RateLimiterConfig.custom()
                                                     .limitForPeriod(requests)
                                                     .limitRefreshPeriod(period)
                                                     .timeoutDuration(maxWait)
                                                     .build());
    }

    static Bulkhead bulkhead(String name, int parallelRequests, Duration maxWait) {
        return Bulkhead.of(name, BulkheadConfig.custom()
                                               .maxConcurrentCalls(parallelRequests)
                                               .maxWaitDuration(maxWait)
                                               .build());
    }

    static Retry retry(String name, int maxAttempts, Duration initialWait, Predicate<Throwable> isRetryable) {
        return Retry.of(name, RetryConfig.custom()
                                         .maxAttempts(maxAttempts)
                                         .retryOnException(isRetryable)
                                         .intervalFunction(IntervalFunction.ofExponentialBackoff(initialWait, 2))
                                         .build());
    }

    static boolean isTooManyRequests(Throwable throwable) {
        return throwable instanceof FetcherException fetcherException
                && fetcherException.getHttpResponse()
                                   .filter(response -> response.statusCode() == HTTP_TOO_MANY_REQUESTS)
                                   .isPresent();
    }

    static boolean hasParameter(URL url, String name) {
        NameValuePair parameter = uriBuilder(url).getFirstQueryParam(name);
        return parameter != null && parameter.getValue() != null && !parameter.getValue().isBlank();
    }

    static URL addParameterIfAbsent(URL url, String name, Optional<String> value) {
        if (value.isEmpty() || hasParameter(url, name)) {
            return url;
        }
        try {
            return uriBuilder(url).addParameter(name, value.get()).build().toURL();
        } catch (URISyntaxException | MalformedURLException e) {
            throw new IllegalArgumentException("Cannot add a parameter to " + url, e);
        }
    }

    private static URIBuilder uriBuilder(URL url) {
        try {
            return new URIBuilder(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Not a valid URI: " + url, e);
        }
    }
}

package org.jabref.logic.net.provider;

import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.jabref.logic.importer.FetcherClientException;
import org.jabref.model.http.SimpleHttpResponse;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ProviderPolicyTest {

    private URL url;
    private Bulkhead bulkhead;
    private ProviderPolicy policy;

    @BeforeEach
    void setUp() throws Exception {
        url = URI.create("https://api.example.org/works").toURL();
        bulkhead = ProviderSupport.bulkhead("test", 1, Duration.ZERO);
        RateLimiter rateLimiter = ProviderSupport.rateLimiter("test", 100, Duration.ofSeconds(1), Duration.ZERO);
        Retry retry = ProviderSupport.retry("test", 3, Duration.ofMillis(1), ProviderSupport::isTooManyRequests);
        policy = new ProviderPolicy() {
            @Override
            public String getProviderName() {
                return "Test";
            }

            @Override
            public boolean appliesTo(URL url) {
                return true;
            }

            @Override
            public RateLimiter rateLimiterFor(URL url) {
                return rateLimiter;
            }

            @Override
            public Bulkhead bulkheadFor(URL url) {
                return bulkhead;
            }

            @Override
            public Retry retry() {
                return retry;
            }
        };
    }

    @Test
    void retriesTooManyRequestsUntilSuccess() throws Exception {
        URLConnection connection = mock(URLConnection.class);
        AtomicInteger attempts = new AtomicInteger();

        URLConnection result = policy.execute(url, () -> {
            if (attempts.incrementAndGet() < 3) {
                throw tooManyRequests();
            }
            return connection;
        });

        assertSame(connection, result);
        assertEquals(3, attempts.get());
    }

    @Test
    void releasesParallelRequestPermitWhenAllAttemptsFail() {
        assertThrows(FetcherClientException.class, () -> policy.execute(url, () -> {
            throw tooManyRequests();
        }));

        assertEquals(1, bulkhead.getMetrics().getAvailableConcurrentCalls());
    }

    @ParameterizedTest
    @MethodSource("policies")
    // [utest->req~fetchers.identifier-rate-limit-retries~1]
    void everyPolicyRetriesTooManyRequestsOnly(ProviderPolicy providerPolicy) {
        Predicate<Throwable> retries = providerPolicy.retry().getRetryConfig().getExceptionPredicate();

        assertTrue(retries.test(tooManyRequests()));
        assertFalse(retries.test(new FetcherClientException(url, new SimpleHttpResponse(404, "Not Found", ""))));
    }

    static Stream<ProviderPolicy> policies() {
        return Stream.of(new CrossrefPolicy(Optional::empty), new OpenAlexPolicy(Optional::empty), new OpenCitationsPolicy(Optional::empty), new SemanticScholarPolicy(Optional::empty));
    }

    private FetcherClientException tooManyRequests() {
        return new FetcherClientException(url, new SimpleHttpResponse(429, "Too Many Requests", ""));
    }
}

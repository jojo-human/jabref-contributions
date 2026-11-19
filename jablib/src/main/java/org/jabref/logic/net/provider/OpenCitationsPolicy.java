package org.jabref.logic.net.provider;

import java.net.URL;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import org.jspecify.annotations.NullMarked;

/// OpenCitations allows 180 requests per minute ([API documentation](https://api.opencitations.net/index/v2))
@NullMarked
// [impl->req~fetchers.rate-limiting~1]
final class OpenCitationsPolicy implements ProviderPolicy {

    static final String HOST = "api.opencitations.net";

    private static final Duration MAX_WAIT_FOR_PERMIT = Duration.ofSeconds(30);
    private static final Duration MAX_WAIT_FOR_SLOT = Duration.ofSeconds(30);

    private static final RateLimiter RATE = ProviderSupport.rateLimiter("opencitations", 3, Duration.ofSeconds(1), MAX_WAIT_FOR_PERMIT);

    private static final Bulkhead PARALLEL = ProviderSupport.bulkhead("opencitations", 2, MAX_WAIT_FOR_SLOT);

    // [impl->req~fetchers.identifier-rate-limit-retries~1]
    private static final Retry RETRY = ProviderSupport.retry("opencitations", 3, Duration.ofSeconds(1), ProviderSupport::isTooManyRequests);

    private final Supplier<Optional<String>> accessToken;

    OpenCitationsPolicy(Supplier<Optional<String>> accessToken) {
        this.accessToken = accessToken;
    }

    @Override
    public String getProviderName() {
        return "OpenCitations";
    }

    @Override
    public boolean appliesTo(URL url) {
        return HOST.equalsIgnoreCase(url.getHost());
    }

    @Override
    public Map<String, String> identificationHeaders() {
        return accessToken.get().map(key -> Map.of("authorization", key)).orElse(Map.of());
    }

    @Override
    public RateLimiter rateLimiterFor(URL url) {
        return RATE;
    }

    @Override
    public Bulkhead bulkheadFor(URL url) {
        return PARALLEL;
    }

    @Override
    public Retry retry() {
        return RETRY;
    }
}

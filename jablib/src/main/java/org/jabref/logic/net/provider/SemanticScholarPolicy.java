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

/// With an API key, Semantic Scholar allows 1 request per second ([tutorial](https://www.semanticscholar.org/product/api/tutorial)).
@NullMarked
// [impl->req~fetchers.rate-limiting~1]
final class SemanticScholarPolicy implements ProviderPolicy {

    static final String HOST = "api.semanticscholar.org";

    private static final String API_KEY_HEADER = "x-api-key";

    private static final Duration MAX_WAIT_FOR_PERMIT = Duration.ofSeconds(30);
    private static final Duration MAX_WAIT_FOR_SLOT = Duration.ofSeconds(30);

    private static final RateLimiter RATE = ProviderSupport.rateLimiter("semanticscholar", 1, Duration.ofSeconds(1), MAX_WAIT_FOR_PERMIT);

    private static final Bulkhead PARALLEL = ProviderSupport.bulkhead("semanticscholar", 1, MAX_WAIT_FOR_SLOT);

    // [impl->req~fetchers.identifier-rate-limit-retries~1]
    private static final Retry RETRY = ProviderSupport.retry("semanticscholar", 4, Duration.ofSeconds(1), ProviderSupport::isTooManyRequests);

    private final Supplier<Optional<String>> apiKey;

    SemanticScholarPolicy(Supplier<Optional<String>> apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String getProviderName() {
        return "Semantic Scholar";
    }

    @Override
    public boolean appliesTo(URL url) {
        return HOST.equalsIgnoreCase(url.getHost());
    }

    @Override
    public Map<String, String> identificationHeaders() {
        return apiKey.get().map(key -> Map.of(API_KEY_HEADER, key)).orElse(Map.of());
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

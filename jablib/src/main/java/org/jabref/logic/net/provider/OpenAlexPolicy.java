package org.jabref.logic.net.provider;

import java.net.URL;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import org.jspecify.annotations.NullMarked;

/// The provider's current limits are listed under [Rate limits](https://help.openalex.org/how-to-use-the-api/rate-limits-and-authentication).
@NullMarked
// [impl->req~fetchers.rate-limiting~1]
final class OpenAlexPolicy implements ProviderPolicy {

    static final String HOST = "api.openalex.org";

    private static final Duration MAX_WAIT_FOR_PERMIT = Duration.ofSeconds(30);
    private static final Duration MAX_WAIT_FOR_SLOT = Duration.ofSeconds(30);

    private static final String API_KEY = "api_key";

    private static final RateLimiter WITHOUT_KEY_RATE = ProviderSupport.rateLimiter("openalex-without-key", 10, Duration.ofSeconds(1), MAX_WAIT_FOR_PERMIT);
    private static final RateLimiter WITH_KEY_RATE = ProviderSupport.rateLimiter("openalex-with-key", 30, Duration.ofSeconds(1), MAX_WAIT_FOR_PERMIT);
    private static final Bulkhead PARALLEL = ProviderSupport.bulkhead("openalex", 5, MAX_WAIT_FOR_SLOT);

    // [impl->req~fetchers.identifier-rate-limit-retries~1]
    private static final Retry RETRY = ProviderSupport.retry("openalex", 3, Duration.ofSeconds(1), ProviderSupport::isTooManyRequests);

    private final Supplier<Optional<String>> apiKey;

    OpenAlexPolicy(Supplier<Optional<String>> apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public String getProviderName() {
        return "OpenAlex";
    }

    @Override
    public boolean appliesTo(URL url) {
        return HOST.equalsIgnoreCase(url.getHost());
    }

    @Override
    public URL identify(URL url) {
        return ProviderSupport.addParameterIfAbsent(url, API_KEY, apiKey.get());
    }

    @Override
    public RateLimiter rateLimiterFor(URL url) {
        return ProviderSupport.hasParameter(url, API_KEY) ? WITH_KEY_RATE : WITHOUT_KEY_RATE;
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

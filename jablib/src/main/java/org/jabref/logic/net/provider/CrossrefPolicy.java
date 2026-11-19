package org.jabref.logic.net.provider;

import java.net.URL;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import org.jspecify.annotations.NullMarked;

/// Limits differ by pool and request type ([announcement](https://www.crossref.org/blog/announcing-changes-to-rest-api-rate-limits/)).
@NullMarked
// [impl->req~fetchers.rate-limiting~1]
final class CrossrefPolicy implements ProviderPolicy {

    static final String HOST = "api.crossref.org";

    /// A single record is addressed by one path segment after the resource, e.g. `/works/{doi}` or `/journals/{issn}`.
    /// Everything else (`/works?query=…`, `/members/{id}/works`) is a list.
    private static final Pattern SINGLE_RECORD_PATH = Pattern.compile("^/(?:v1/)?[a-z]+/[^/]+$");

    private static final Duration MAX_WAIT_FOR_PERMIT = Duration.ofSeconds(30);
    private static final Duration MAX_WAIT_FOR_SLOT = Duration.ofSeconds(30);

    private static final String MAILTO = "mailto";

    private static final RateLimiter PUBLIC_LIST_RATE = ProviderSupport.rateLimiter("crossref-public-list", 1, Duration.ofSeconds(1), MAX_WAIT_FOR_PERMIT);
    private static final RateLimiter PUBLIC_SINGLE_RATE = ProviderSupport.rateLimiter("crossref-public-single", 5, Duration.ofSeconds(1), MAX_WAIT_FOR_PERMIT);
    private static final RateLimiter POLITE_LIST_RATE = ProviderSupport.rateLimiter("crossref-polite-list", 3, Duration.ofSeconds(1), MAX_WAIT_FOR_PERMIT);
    private static final RateLimiter POLITE_SINGLE_RATE = ProviderSupport.rateLimiter("crossref-polite-single", 10, Duration.ofSeconds(1), MAX_WAIT_FOR_PERMIT);

    private static final Bulkhead PUBLIC_PARALLEL = ProviderSupport.bulkhead("crossref-public", 1, MAX_WAIT_FOR_SLOT);
    private static final Bulkhead POLITE_PARALLEL = ProviderSupport.bulkhead("crossref-polite", 3, MAX_WAIT_FOR_SLOT);

    // [impl->req~fetchers.identifier-rate-limit-retries~1]
    private static final Retry RETRY = ProviderSupport.retry("crossref", 3, Duration.ofSeconds(1), ProviderSupport::isTooManyRequests);

    private final Supplier<Optional<String>> contactEmail;

    CrossrefPolicy(Supplier<Optional<String>> contactEmail) {
        this.contactEmail = contactEmail;
    }

    @Override
    public String getProviderName() {
        return "Crossref";
    }

    @Override
    public boolean appliesTo(URL url) {
        return HOST.equalsIgnoreCase(url.getHost());
    }

    @Override
    // [impl->req~fetchers.crossref-polite-pool~1]
    public URL identify(URL url) {
        return ProviderSupport.addParameterIfAbsent(url, MAILTO, contactEmail.get());
    }

    @Override
    public RateLimiter rateLimiterFor(URL url) {
        boolean singleRecord = SINGLE_RECORD_PATH.matcher(url.getPath()).matches();
        if (ProviderSupport.hasParameter(url, MAILTO)) {
            return singleRecord ? POLITE_SINGLE_RATE : POLITE_LIST_RATE;
        }
        return singleRecord ? PUBLIC_SINGLE_RATE : PUBLIC_LIST_RATE;
    }

    @Override
    public Bulkhead bulkheadFor(URL url) {
        return ProviderSupport.hasParameter(url, MAILTO) ? POLITE_PARALLEL : PUBLIC_PARALLEL;
    }

    @Override
    public Retry retry() {
        return RETRY;
    }
}

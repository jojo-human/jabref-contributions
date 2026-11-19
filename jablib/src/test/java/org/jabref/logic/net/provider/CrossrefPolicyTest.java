package org.jabref.logic.net.provider;

import java.net.URI;
import java.net.URL;
import java.util.Optional;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrossrefPolicyTest {

    private static final CrossrefPolicy POLICY = new CrossrefPolicy(Optional::empty);

    @ParameterizedTest
    @CsvSource({
            "https://api.crossref.org/works?query=test, crossref-public-list, crossref-public",
            "https://api.crossref.org/works/10.1000%2F1?mailto=user%40example.org, crossref-polite-single, crossref-polite",
            "https://api.crossref.org/members/13/works?mailto=user%40example.org, crossref-polite-list, crossref-polite",
            "https://api.crossref.org/journals/0266-612X, crossref-public-single, crossref-public"
    })
    // [utest->req~fetchers.rate-limiting~1]
    void limitsFollowPoolAndRequestType(String url, String expectedRateLimiter, String expectedBulkhead) throws Exception {
        URL parsed = URI.create(url).toURL();

        assertEquals(expectedRateLimiter, POLICY.rateLimiterFor(parsed).getName());
        assertEquals(expectedBulkhead, POLICY.bulkheadFor(parsed).getName());
    }

    @ParameterizedTest
    @CsvSource({
            "https://api.crossref.org/works?query=test, user@example.org, https://api.crossref.org/works?query=test&mailto=user%40example.org",
            "https://api.crossref.org/works?query=test&mailto=other%40example.org, user@example.org, https://api.crossref.org/works?query=test&mailto=other%40example.org",
            "https://api.crossref.org/works?query=test, '', https://api.crossref.org/works?query=test"
    })
    // [utest->req~fetchers.crossref-polite-pool~1]
    void addsConfiguredEmailUnlessPresent(String url, String email, String expected) throws Exception {
        Optional<String> contactEmail = Optional.of(email).filter(value -> !value.isEmpty());

        assertEquals(expected, new CrossrefPolicy(() -> contactEmail).identify(URI.create(url).toURL()).toString());
    }
}

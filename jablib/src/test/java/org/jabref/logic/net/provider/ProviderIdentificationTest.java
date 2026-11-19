package org.jabref.logic.net.provider;

import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderIdentificationTest {

    private static final Supplier<Optional<String>> KEY = () -> Optional.of("secret");
    private static final Supplier<Optional<String>> NO_KEY = Optional::empty;

    private static final List<ProviderPolicy> POLICIES = List.of(
            new CrossrefPolicy(NO_KEY),
            new OpenAlexPolicy(NO_KEY),
            new OpenCitationsPolicy(NO_KEY),
            new SemanticScholarPolicy(NO_KEY));

    @ParameterizedTest
    @CsvSource({
            "https://api.openalex.org/works?search=test, true, https://api.openalex.org/works?search=test&api_key=secret, openalex-with-key",
            "https://api.openalex.org/works?search=test, false, https://api.openalex.org/works?search=test, openalex-without-key"
    })
    // [utest->req~fetchers.rate-limiting~1]
    void openAlexAddsKeyAndSelectsItsLimit(String url, boolean withKey, String expectedUrl, String expectedRateLimiter) throws Exception {
        OpenAlexPolicy policy = new OpenAlexPolicy(withKey ? KEY : NO_KEY);

        URL identified = policy.identify(URI.create(url).toURL());

        assertEquals(expectedUrl, identified.toString());
        assertEquals(expectedRateLimiter, policy.rateLimiterFor(identified).getName());
    }

    @Test
    void headerKeysAreSentOnlyWhenConfigured() {
        assertEquals(Map.of("x-api-key", "secret"), new SemanticScholarPolicy(KEY).identificationHeaders());
        assertEquals(Map.of("authorization", "secret"), new OpenCitationsPolicy(KEY).identificationHeaders());
        assertEquals(Map.of(), new SemanticScholarPolicy(NO_KEY).identificationHeaders());
    }

    @ParameterizedTest
    @CsvSource({
            "https://api.openalex.org/works, OpenAlex",
            "https://api.semanticscholar.org/graph/v1/paper/x, Semantic Scholar",
            "https://api.opencitations.net/index/v2/citations/x, OpenCitations",
            "https://API.CROSSREF.ORG/works, Crossref"
    })
    void looksUpPolicyByHost(String url, String expectedProvider) throws Exception {
        URL parsed = URI.create(url).toURL();

        assertEquals(List.of(expectedProvider), POLICIES.stream()
                                                                .filter(policy -> policy.appliesTo(parsed))
                                                                .map(ProviderPolicy::getProviderName)
                                                                .toList());
    }

    @ParameterizedTest
    @CsvSource({
            "https://openalex.org/W123",
            "https://www.semanticscholar.org/paper/x",
            "https://opencitations.net/accesstoken",
            "https://doi.org/10.1000/1",
            "https://example.org/works?api_key=x"
    })
    void addsNothingToOtherHosts(String url) throws Exception {
        URL parsed = URI.create(url).toURL();

        assertTrue(POLICIES.stream().noneMatch(policy -> policy.appliesTo(parsed)));
    }
}

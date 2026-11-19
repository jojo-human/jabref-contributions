package org.jabref.logic.net.provider;

import java.net.URL;
import java.util.List;
import java.util.Optional;

import org.jabref.logic.importer.ImporterPreferences;
import org.jabref.logic.importer.fetcher.CrossRef;
import org.jabref.logic.importer.fetcher.OpenAlex;
import org.jabref.logic.importer.fetcher.SemanticScholar;
import org.jabref.logic.importer.fetcher.citation.opencitations.OpenCitationsFetcher;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class ProviderPolicies {

    public static final ProviderPolicies NONE = new ProviderPolicies(List.of());

    private final List<ProviderPolicy> policies;

    public ProviderPolicies(ImporterPreferences importerPreferences) {
        this.policies = List.of(
                new CrossrefPolicy(() -> importerPreferences.getApiKey(CrossRef.FETCHER_NAME)),
                new OpenAlexPolicy(() -> importerPreferences.getApiKey(OpenAlex.FETCHER_NAME)),
                new OpenCitationsPolicy(() -> importerPreferences.getApiKey(OpenCitationsFetcher.FETCHER_NAME)),
                new SemanticScholarPolicy(() -> importerPreferences.getApiKey(SemanticScholar.FETCHER_NAME)));
    }

    private ProviderPolicies(List<ProviderPolicy> policies) {
        this.policies = policies;
    }

    public Optional<ProviderPolicy> forUrl(URL url) {
        return policies.stream().filter(policy -> policy.appliesTo(url)).findFirst();
    }
}

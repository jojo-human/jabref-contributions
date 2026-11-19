package org.jabref.logic.net;

import java.net.URL;

import org.jabref.logic.importer.ImporterPreferences;
import org.jabref.logic.net.provider.ProviderPolicies;

import org.jspecify.annotations.NullMarked;

/// Factory for creating [URLDownload]s for the resources a fetcher reads.
///
/// TODO: For now, a fetcher builds its `WebResources` from the `ImporterPreferences` it receives.
/// Later, fetchers should get it injected.
@NullMarked
public final class WebResources {

    /// TODO: only used for this, remove soon: Passed on to the downloads for the Unpaywall key, which `URLDownload` adds on a redirect to Unpaywall.
    private final ImporterPreferences importerPreferences;
    private final ProviderPolicies policies;

    public WebResources(ImporterPreferences importerPreferences) {
        this.importerPreferences = importerPreferences;
        this.policies = new ProviderPolicies(importerPreferences);
    }

    public URLDownload of(URL url) {
        return new URLDownload(importerPreferences, url, policies);
    }
}

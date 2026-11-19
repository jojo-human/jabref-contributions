---
nav_order: 83
parent: Decision Records
---

# Place rate limiting, retries and provider identification in provider policies

`adr~fetchers.provider-policies~1`

Needs: impl

## Context and Problem Statement

JabRef's fetchers query external providers such as Crossref or Semantic Scholar. Each provider has rules for requests to it:

* a **rate limit**: how many requests per second are allowed (`req~fetchers.rate-limiting~1`),
* a **parallel request limit**: how many requests may run at the same time,
* **retries**: when the provider answers HTTP 429 ("Too Many Requests"), JabRef waits and sends the request again (`req~fetchers.identifier-rate-limit-retries~1`),
* **identification**: an API key or a contact email sent with each request (e.g. `req~fetchers.crossref-polite-pool~1`).

This has two problems:

* Limits are counted per fetcher class, not per provider. Three fetchers call Crossref with three independent limiters.
* The rules depend on each other. For Crossref, the contact email (`mailto` parameter) moves a request to the "polite" pool, which has higher limits. A fetcher that forgets the email gets the lower limits.

Where should these provider rules live?

## Decision Drivers

* A provider's rules hold for every request to that provider, regardless of which fetcher sends it.
* Identification and the limit it selects are decided in one place.
* Separation of responsibilities: a fetcher author needs to know the provider's API (URLs, parameters, response format), not how limits, retries or identification work.

## Considered Options

* Per fetcher, with limiters shared per provider
* Provider policy chosen by the caller and applied by `URLDownload`
* Provider policy looked up by URL in `URLDownload`

## Decision Outcome

Chosen option: "Provider policy looked up by URL in `URLDownload`", because the rules are applied in `URLDownload`, the infrastructure layer that most fetchers already use to send requests.
This separates responsibilities: fetcher authors build the URL and parse the response; the provider's rules are kept in its policy and apply without them doing anything.
Example: `CrossRefCitationFetcher` sends no `mailto` today and therefore gets Crossref's lower limits. With the lookup by URL, it gets the email and the matching limits without a change.

A **provider policy** bundles the rules of one provider: its rate limit, its parallel request limit, its retries and its identification.

### Consequences

* Good, because all requests to a provider share one set of limits and one identification.
* Good, because `FetcherRetry` calls, per-fetcher limiters and identification code are removed from the fetchers.
* Bad, because code that does not use `URLDownload` gets no policy until it moves to `URLDownload`.
  For Crossref, `JournalInformationFetcher` (Unirest) and `DoiFetcher` (requests to `doi.org`, redirected to Crossref inside Java) keep their own limiters until then.
* Bad, because `FetcherRetry` has to be removed from `IdParserFetcher` together with the first policy. Otherwise both retry the same request and the attempts multiply.

## Pros and Cons of the Options

### Per fetcher, with limiters shared per provider

The current approach, with one limiter instance per provider (e.g. `ProviderLimits.CROSSREF`) instead of one per fetcher class.

* Good, because each fetcher's behaviour is visible in its code.
* Good, because it works with any HTTP client the fetcher uses (`URLDownload`, Unirest, Jsoup), and needs no change to `URLDownload`.
* Neutral, because each fetcher holds its parallel request slot until it has read the response, and has to release it itself.
* Bad, because every fetcher author has to wait for the shared limiter before every attempt, apply the provider's retry rules and add the identification, the same way as all other fetchers of that provider.

### Provider policy chosen by the caller and applied by `URLDownload`

```java
new URLDownload(url).withPolicy(ProviderPolicies.CROSSREF)
```

Typically set once in a fetcher's `getUrlDownload(URL)`.

* Good, because the rules are applied correctly on every attempt, without the fetcher author implementing them.
* Good, because the policy is visible at the call site.
* Good, because the caller knows the provider even when the URL does not show it. `DoiFetcher` asks `doi.org`, which serves both Crossref and DataCite.
* Bad, because a caller can forget to set the policy.

### Provider policy looked up by URL in `URLDownload`

```java
new URLDownload(url)   // URLDownload finds the policy for api.crossref.org
```

`ProviderPolicies` matches the provider's API host and, where needed, a path (Crossref has different limits for `/works?query=…` and `/works/{doi}`). Hosts without a policy, e.g. for PDF downloads, are not limited.

* Good, because every request through `URLDownload` gets its provider's rules, without any action by the caller.
* Neutral, because a caller that needs different rules for the same endpoint needs an override (`withPolicy`, `withoutPolicy`), i.e. the previous option as an exception.
* Bad, because the applied rules are not visible at the call site. A debug log shows which policy was applied.

## More Information

* A new provider gets a policy in `org.jabref.logic.net.provider` with its host, identification and limits, registered in `ProviderPolicies`. The Javadoc links the provider's documentation of its limits.

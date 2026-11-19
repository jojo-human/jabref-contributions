---
nav_order: 82
parent: Decision Records
---

# Choose a library for rate limiting and retries of provider requests

`adr~fetchers.resilience-library~1`

Needs: impl

## Context and Problem Statement

JabRef has to stay below the request limits of the providers it queries (`req~fetchers.rate-limiting~1`) and retries requests rejected with HTTP 429 (`req~fetchers.identifier-rate-limit-retries~1`).
Today it uses Guava's `RateLimiter` (wrapped in `FetcherRateLimiter`) and its own `FetcherRetry`.

Providers need more than a smooth rate:

* limits on parallel requests (Crossref: 1 or 3, depending on the pool, see [Crossref's announcement](https://www.crossref.org/blog/announcing-changes-to-rest-api-rate-limits/)),
* limits per longer interval (Software Heritage, queried by `SwhidFetcher`: 120 per hour),
* rates that change at runtime (`DoiFetcher.updateCrossrefAPIRate` reads `X-Rate-Limit-*` headers and calls `setRate`),
* retry delays taken from the response (`Retry-After`).

Which library should JabRef use for these policies?

## Decision Drivers

* Covers rate limits per interval, parallel request limits, retries with a delay computed from the failure, and rate changes at runtime.
* Fits JabRef's module build, which rejects automatic modules (`failOnAutomaticModules = true` in `build-logic`).
* Small dependency footprint and an actively maintained project.

## Considered Options

* Resilience4j
* Failsafe
* Extend JabRef's own code (Guava `RateLimiter`, `Semaphore`, `FetcherRetry`)

## Decision Outcome

Chosen option: "Resilience4j", because it is the only option that adapts the limits of a running policy, and it is actively maintained.

* **Adapting to the provider.** Providers announce their limits in response headers (`X-Rate-Limit-*`, `Retry-After`).
  Resilience4j changes the rate and the parallel request limit of a running limiter (`changeLimitForPeriod`, `Bulkhead.changeConfig`) without resetting its state,
  can drain the remaining permits after a 429 (`drainPermissionsOnResult`),
  and computes the delay of each retry from the failure (`intervalBiFunction`), e.g. from `Retry-After`.
  Failsafe has to replace its limiter, which resets its state. JabRef's own code would need each of these written and maintained by JabRef.
* **Maintenance.** Resilience4j has regular releases. Failsafe's last release is from June 2023.

### Consequences

* Good, because the policies can follow a provider's limits at runtime instead of hard-coded values (today only `DoiFetcher` does this, with its own code).
* Good, because `FetcherRateLimiter`, `FetcherRetry` and `FetcherRetryPolicy` can be replaced by one library.
* Neutral, because pausing a whole provider for the full `Retry-After` is not covered by the library. The policy keeps a "paused until" time and checks it before the rate permit.
* Neutral, because the total time of a request is not bounded by the library. It is bounded by the number of attempts, the backoff and the maximum wait for a permit.
* Bad, because the four artifacts are automatic modules and need entries in `extraJavaModuleInfo`. The versions are aligned with `resilience4j-bom`.

### Confirmation

An ArchUnit rule forbids the rate limiting and retry classes that are replaced, once the migration is complete.

## Pros and Cons of the Options

### Resilience4j

[Resilience4j](https://resilience4j.readme.io), modules `resilience4j-ratelimiter`, `-retry`, `-bulkhead` and `-core`. `resilience4j-all` is not needed and would pull in Micrometer and the JCache API.

```java
RateLimiter limiter = RateLimiter.of("crossref", RateLimiterConfig.custom()
        .limitForPeriod(3)
        .limitRefreshPeriod(Duration.ofSeconds(1))
        .build());
limiter.changeLimitForPeriod(5);   // adapt at runtime
```

* Good, because it covers rate limits per period, parallel request limits (`Bulkhead`) and retries with a delay computed from the failure (`intervalBiFunction`).
* Good, because the rate can change at runtime (`changeLimitForPeriod`).
* Good, because it is actively maintained.
* Bad, because the jars (split per pattern, version-aligned via BOM) ship no `module-info`, only an `Automatic-Module-Name`, so each needs an entry in JabRef's `extraJavaModuleInfo` configuration (`failOnAutomaticModules = true`).
* Bad, because it adds four artifacts (about 250 KB).

### Failsafe

[Failsafe](https://failsafe.dev), one artifact.

```java
RateLimiter<Object> limiter = RateLimiter.burstyBuilder(3, Duration.ofSeconds(1)).build();
Failsafe.with(retryPolicy, bulkhead, limiter).get(() -> download());
```

* Good, because it covers rate limits per period (`burstyBuilder`) or smooth (`smoothBuilder`), parallel request limits (`Bulkhead`) and retries with a delay computed from the failure (`withDelayFn`).
* Good, because it is one artifact (144 KB) without dependencies and with a real `module-info` (`dev.failsafe.core`).
* Bad, because a `RateLimiter` cannot change its rate. Adapting to response headers means replacing the limiter instance.
* Bad, because the last release is from June 2023. Since then there were only CI and test commits.

### Extend JabRef's own code (Guava `RateLimiter`, `Semaphore`, `FetcherRetry`)

```java
CROSSREF_RATE.acquire(url.toString());          // Guava RateLimiter, smooth rate
CROSSREF_PARALLEL.acquire();                     // java.util.concurrent.Semaphore
FetcherRetry.executeWithRetryPolicy(policy, () -> download());
```

* Good, because no new dependency is needed and the existing code is tested.
* Good, because Guava's `RateLimiter` can change its rate (`setRate`).
* Good, because a parallel request limit is a `Semaphore`, and reading `Retry-After` is a small addition to `FetcherRetry`.
* Neutral, because Guava's `RateLimiter` only spreads requests evenly. It never exceeds a limit. With 120 per hour, a single request is sent at once, but several requests in a row are sent 30 s apart, where a limit per interval would send them at once.
* Bad, because every further policy (e.g. a circuit breaker) has to be written, tested and maintained by JabRef.

## More Information

* `SimpleHttpResponse` does not carry response headers today. Reading `Retry-After` needs this change with every option.

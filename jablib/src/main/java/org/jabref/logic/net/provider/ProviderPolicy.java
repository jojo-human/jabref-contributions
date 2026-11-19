package org.jabref.logic.net.provider;

import java.net.URL;
import java.net.URLConnection;
import java.util.Map;

import org.jabref.logic.importer.FetcherException;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.core.exception.AcquirePermissionCancelledException;
import io.github.resilience4j.core.functions.CheckedSupplier;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.retry.Retry;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface ProviderPolicy {

    String getProviderName();

    boolean appliesTo(URL url);

    default URL identify(URL url) {
        return url;
    }

    default Map<String, String> identificationHeaders() {
        return Map.of();
    }

    RateLimiter rateLimiterFor(URL url);

    Bulkhead bulkheadFor(URL url);

    Retry retry();

    /// The retry is the outermost step, so every retry takes a new slot and a new permit.
    default URLConnection execute(URL url, CheckedSupplier<URLConnection> connect) throws FetcherException {
        RateLimiter rateLimiter = rateLimiterFor(url);
        Bulkhead bulkhead = bulkheadFor(url);
        CheckedSupplier<URLConnection> attempt = Bulkhead.decorateCheckedSupplier(bulkhead, () -> {
            RateLimiter.waitForPermission(rateLimiter);
            return connect.get();
        });
        try {
            return Retry.decorateCheckedSupplier(retry(), attempt).get();
        } catch (FetcherException e) {
            throw e;
        } catch (AcquirePermissionCancelledException e) {
            Thread.currentThread().interrupt();
            throw new FetcherException("Interrupted while waiting for " + getProviderName(), e);
        } catch (RequestNotPermitted | BulkheadFullException e) {
            if (Thread.currentThread().isInterrupted()) {
                throw new FetcherException("Interrupted while waiting for " + getProviderName(), e);
            }
            throw new FetcherException("Waited too long for a free request slot at " + getProviderName(), e);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable throwable) {
            throw new FetcherException("Request to " + getProviderName() + " failed", throwable);
        }
    }
}

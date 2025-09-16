package org.folio.dataimport.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Log4j2
@Component
public class CancelledJobsIdsCache {

  private final Cache<String, Boolean> cache;

  public CancelledJobsIdsCache(
    @Value("${mod.invoice.cache.cancelled.jobs.expiration.minutes:1440}") long cacheExpirationTimeMins) {
    this.cache = Caffeine.newBuilder().expireAfterWrite(cacheExpirationTimeMins, TimeUnit.MINUTES)
      .build();
  }

  /**
   * Puts the specified {@code jobId} into the cache.
   *
   * @param jobId import job id to put into the cache
   */
  public void put(String jobId) {
    cache.put(jobId, Boolean.TRUE);
  }

  /**
   * Checks if the cache contains the specified {@code jobId}.
   *
   * @param jobId import job id to check
   * @return {@code true} if the cache contains the {@code jobId}, {@code false} otherwise
   */
  public boolean contains(String jobId) {
    return jobId != null && cache.asMap().containsKey(jobId);
  }

}

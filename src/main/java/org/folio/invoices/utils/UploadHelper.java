package org.folio.invoices.utils;

import java.util.concurrent.CompletableFuture;

import org.folio.rest.acq.model.BatchVoucher;

public interface UploadHelper {

  public CompletableFuture<String> login(String username, String password);

  public CompletableFuture<String> logout();

  public CompletableFuture<String> upload(String filename, BatchVoucher batchVoucher);
}
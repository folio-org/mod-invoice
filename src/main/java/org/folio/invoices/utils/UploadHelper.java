package org.folio.invoices.utils;

import org.folio.rest.jaxrs.model.BatchVoucher;
import java.util.concurrent.CompletableFuture;



public interface UploadHelper {

  public CompletableFuture<String> login(String username, String password);

  public CompletableFuture<String> logout();

  public CompletableFuture<String> upload(String filename, BatchVoucher batchVoucher);
}

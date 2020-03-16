package org.folio.invoices.utils;

import java.util.concurrent.CompletableFuture;

public interface UploadHelper {

  public CompletableFuture<String> login(String username, String password);

  public CompletableFuture<String> logout();

  // to be added later (MODINVOICE-133)
  // public ComplateableFuture<String> upload( ... );
}
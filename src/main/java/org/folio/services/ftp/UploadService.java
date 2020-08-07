package org.folio.services.ftp;

import org.folio.rest.jaxrs.model.BatchVoucher;
import java.util.concurrent.CompletableFuture;
import io.vertx.core.Context;


public interface UploadService {
  CompletableFuture<String> login(String username, String password);
  CompletableFuture<String> logout();
  CompletableFuture<String> upload(Context ctx, String filename, String content);
}

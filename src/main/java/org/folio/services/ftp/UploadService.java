package org.folio.services.ftp;


import io.vertx.core.Context;
import io.vertx.core.Future;

public interface UploadService {
  Future<String> login(String username, String password);
  Future<String> logout();
  Future<String> upload(Context ctx, String filename, String content);
}

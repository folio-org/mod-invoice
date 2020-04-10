package org.folio.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public class MessageDTO {
  @JsonProperty("message")
  @JsonPropertyDescription("Free-form message")
  private final String message;

  public MessageDTO(String message) {
    this.message = message;
  }
}

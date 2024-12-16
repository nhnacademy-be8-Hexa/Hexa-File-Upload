package com.nhnacademy.hexafileupload.exception;

public class LocalImageException extends RuntimeException {
  public LocalImageException(String message) {
    super(message);
  }

  public LocalImageException(String message, Throwable cause) {
    super(message, cause);
  }
}

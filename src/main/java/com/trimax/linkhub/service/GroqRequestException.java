package com.trimax.linkhub.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Only application-owned messages and codes may cross the API/log boundary. */
public final class GroqRequestException extends ResponseStatusException {
    private final String code,providerRequestId;
    private final int providerStatus;
    private final boolean retryable;
    private final Integer retryAfterSeconds;

    public GroqRequestException(HttpStatus status,String code,String message,int providerStatus,
                                 String providerRequestId,boolean retryable,Integer retryAfterSeconds) {
        super(status,message);this.code=code;this.providerStatus=providerStatus;this.retryable=retryable;
        this.providerRequestId=providerRequestId!=null&&providerRequestId.matches("req_[A-Za-z0-9_-]{1,100}")?providerRequestId:"unavailable";
        this.retryAfterSeconds=retryAfterSeconds;
    }
    public String code(){return code;}
    public int providerStatus(){return providerStatus;}
    public String providerRequestId(){return providerRequestId;}
    public boolean retryable(){return retryable;}
    public Integer retryAfterSeconds(){return retryAfterSeconds;}
}

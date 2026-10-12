package com.nexusclient.backend;

import org.springframework.http.HttpStatus;

/**
 * NexusException – "this request can't be done", with a plain-English reason the launcher can show
 * (e.g. "You're not friends with Steve"). ErrorReplies turns it into {"error": "..."}.
 */
public class NexusException extends RuntimeException {

    private final HttpStatus status;

    public NexusException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    public static NexusException badRequest(String message) {
        return new NexusException(HttpStatus.BAD_REQUEST, message);
    }

    public static NexusException notFound(String message) {
        return new NexusException(HttpStatus.NOT_FOUND, message);
    }

    public static NexusException forbidden(String message) {
        return new NexusException(HttpStatus.FORBIDDEN, message);
    }

    public static NexusException unauthorized(String message) {
        return new NexusException(HttpStatus.UNAUTHORIZED, message);
    }

    /** 429: too many tries in a short time (see RateLimiter). */
    public static NexusException tooManyRequests(String message) {
        return new NexusException(HttpStatus.TOO_MANY_REQUESTS, message);
    }
}

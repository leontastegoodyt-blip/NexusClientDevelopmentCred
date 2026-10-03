package com.nexusclient.launcher.account;

/**
 * LoginException – something went wrong while signing in.
 * Its message is always written in plain words, ready to show to the player.
 */
public class LoginException extends Exception {

    public LoginException(String message) {
        super(message);
    }

    public LoginException(String message, Throwable cause) {
        super(message, cause);
    }
}

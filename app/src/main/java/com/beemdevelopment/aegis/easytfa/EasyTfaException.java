package com.beemdevelopment.aegis.easytfa;

public class EasyTfaException extends Exception {
    public EasyTfaException(String message) {
        super(message);
    }

    public EasyTfaException(Throwable cause) {
        super(cause);
    }
}

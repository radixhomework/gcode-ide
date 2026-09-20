package dev.radixhomework.gcodeide.model.profiles;

/** Raised when a profile is invalid; the message names the offending field. */
public final class ProfileException extends IllegalArgumentException {
    public ProfileException(String message) {
        super(message);
    }
}

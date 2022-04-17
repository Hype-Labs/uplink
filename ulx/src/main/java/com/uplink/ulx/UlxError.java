package com.uplink.ulx;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * This class works as a container for error information. This class is
 * extensively used by the SDK, especially in delegates. It holds four
 * main properties: a code, a description, a reason for the error, and
 * a recovery suggestion. Error codes are listed under UlxErrorCode, along
 * with their meaning. The description indicates what went wrong, such as
 * "Couldn't send a message". The reason indicates the cause, such as
 * "Bluetooth is not turned on". Continuing on the same example, the recovery
 * suggestion indicates a possible recovery from the error, such as "Try
 * turning Bluetooth on".
 */
public class UlxError {

    private final int code;
    private final String description;
    private final String reason;
    private final String suggestion;

    /**
     * Full constructor of the error code.
     * @param code The error code.
     * @param description The description.
     * @param reason The reason.
     * @param suggestion A recovery suggestion.
     */
    public UlxError(UlxErrorCode code, String description, String reason, String suggestion) {
        this.code = code.getValue();
        this.description = description;
        this.reason = reason;
        this.suggestion = suggestion;
    }

    /**
     * This getter indicates the error code, as listed by the UlxErrorCode
     * enumeration.
     * @return The error code.
     */
    public final int getCode() {
        return this.code;
    }

    /**
     * This getter provides a description of the error, indicating what went
     * wrong. An example could be "Could not send a message".
     * @return The error description.
     */
    @Nullable
    public final String getDescription() {
        return this.description;
    }

    /**
     * The reason key describes the cause for a failure. For instance, if a
     * connection fails, a possible message could be "Device went out of range".
     * @return A description of the cause for the error.
     */
    public final String getReason() {
        return this.reason;
    }

    /**
     * Recovery suggestions indicate possible recovery scenarios from a failure.
     * For instance, if the SDK fails to start, it could say "Try turning the
     * adapter on".
     * @return A recovery suggestion for the error.
     */
    public String getSuggestion() {
        return this.suggestion;
    }

    @NonNull
    @SuppressLint("DefaultLocale")
    @Override
    public String toString() {
        return String.format("Error(Code: %d, Reason: %s, Description: %s, Suggestion: %s)", getCode(), getReason(), getDescription(), getSuggestion());
    }
}

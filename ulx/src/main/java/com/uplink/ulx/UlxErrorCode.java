package com.uplink.ulx;

/**
 * Lists and documents errors and their respective codes. The error descriptions
 * indicate what went wrong in a way that is commonly perceptible, but they may
 * not suitable for displaying to the end user. Errors indicate something that
 * went wrong during runtime, and that most likely could not be prevented by the
 * developer. Preventable issues, such as bad API usage, are flagged with
 * exceptions instead. The error codes can be used for helping with the cause of
 * an issue, as they identify the underlying cause. They are also helpful when
 * seeking support.
 */
public enum UlxErrorCode {

	/**
	 * An unknown error occurred. There are no details about the cause because
	 * it could not be determined. This error should be reserved for abnormal
	 * circumstances, such as being unclear to the implementation what went
	 * wrong.
	 */
	UNKNOWN(0),

	/**
	 * An operation could not be completed because the adapter is disabled.
	 * Implementations must not attempt to turn it on, and instead recommend
	 * the user to do so through recovery suggestions. When applicable,
	 * implementations should subscribe to adapter state changes and attempt
	 * recovery when the adapter is known to be on, after asking a delegate
	 * whether they should.
	 */
	ADAPTER_DISABLED(1),

	/**
	 * An operation could not be completed because adapter activity has not been
	 * authorized by the user and the operating system is denying permission.
	 * Recovery suggestions should advise the user to authorize activity on the
	 * adapter.
	 */
	ADAPTER_UNAUTHORIZED(2),

	/**
	 * The implementation is requesting activity on an adapter that is not
	 * supported by the current platform. Recovery is not possible. Recovery
	 * suggestions should recommend the user to update their systems or
	 * contact the manufacturer.
	 */
	ADAPTER_NOT_SUPPORTED(3),

	/**
	 * An operation cannot be completed because the adapter is busy doing
	 * something else, or the implementation is not allowing it to overlap with
	 * other ongoing activities. The operation will not be scheduled for later,
	 * and is considered to have failed altogether.
	 */
	ADAPTER_BUSY(4),

	/**
	 * A remote peer failed to comply with a protocol specification and the
	 * implementation is rejecting to communicate with it. This probably
	 * indicates an attacker on the network attempting to break through the
	 * protocols. The SDK should reject communicating with the peer by
	 * blacklisting it.
	 */
	PROTOCOL_VIOLATION(5),

	/**
	 * An operation has failed due to a connection not having previously been
	 * established. The implementation should first attempt to connect. The
	 * operation will not attempt to resume.
	 */
	NOT_CONNECTED(6),

	/**
	 * A connection request has failed because the peer is not connectable.
	 * Implementations should not reattempt to connect. The operation will not
	 * attempt to resume and instead must be manually retried.
	 */
	NOT_CONNECTABLE(7),

	/**
	 * An operation failed because the connection timed out. Implementations
	 * should attempt to reconnect before proceeding. The operation will not
	 * attempt to resume and instead must be manually retried.
	 */
	CONNECTION_TIMEOUT(8),

	/**
	 * An operation failed because the stream is not open. The implementation
	 * should first attempt to open it.
	 */
	STREAM_IS_NOT_OPEN(9),
	;
	
	private final int code;

	/**
	 * Constructor.
	 * @param code A numeric error code.
	 */
	UlxErrorCode(int code) {
		this.code = code;
	}

	/**
	 * Getter for the enumeration error code.
	 * @return The error code.
	 */
	public int getValue() {
		return code;
	}
	
	/**
	 * Translates a numeric error code into an UlxErrorCode instance. If the
	 * code is not recognized this method returns null.
	 * @param value The value to translate.
	 * @return A UlxErrorCode or null, if the code is not recognized.
	 */
	public static UlxErrorCode getValue(int value) {
		for (UlxErrorCode error: UlxErrorCode.values()) {
	  		if (error.getValue() == value) {
	   			return error;
	  		}
	 	}

		// Not found
	 	return null;
	}
}

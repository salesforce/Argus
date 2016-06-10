package com.salesforce.dva.warden.client.util;

public class Assert {

	 //~ Constructors *********************************************************************************************************************************

    /* Private constructor to prevent instantiation. */
    private Assert() {
        assert (false) : "This class should never be instantiated.";
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Throws an IllegalArgumentException if the condition is not met.
     *
     * @param  condition  The condition to evaluate,
     * @param  message    The exception message.
     */
    public static void requireArgument(boolean condition, String message) {
        require(condition, message, IllegalArgumentException.class);
    }

    /**
     * Throws an IllegalStateException if the condition is not met.
     *
     * @param  condition  The condition to evaluate,
     * @param  message    The exception message.
     */
    public static void requireState(boolean condition, String message) {
        require(condition, message, IllegalStateException.class);
    }

    private static <T extends RuntimeException> void require(boolean condition, String message, Class<T> type) throws WardenException {
        if (!condition) {
            RuntimeException result;

            try {
                result = type.getConstructor(String.class).newInstance(message);
            } catch (Exception ex) {
                throw new WardenException(ex);
            }
            throw result;
        }
    }
}

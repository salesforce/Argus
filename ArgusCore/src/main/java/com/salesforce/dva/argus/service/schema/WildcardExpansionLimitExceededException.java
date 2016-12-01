package com.salesforce.dva.argus.service.schema;

/**
 * Indicates a wildcard query expansion has exceeded the allowed limit.
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */

@SuppressWarnings("serial")
public class WildcardExpansionLimitExceededException extends RuntimeException {

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new WildcardExpansionLimitExceededException object.
     *
     * @param  msg  The exception message.
     */
    public WildcardExpansionLimitExceededException(String msg) {
        super(msg);
    }

    /**
     * Creates a new WildcardExpansionLimitExceededException object.
     *
     * @param  cause  The cause of the exception.
     */
    public WildcardExpansionLimitExceededException(Throwable cause) {
        super(cause);
    }

    /**
     * Creates a new WildcardExpansionLimitExceededException object.
     *
     * @param  msg  The exception message.
     * @param  ex   The cause of the exception.
     */
    public WildcardExpansionLimitExceededException(String msg, Throwable ex) {
        super(msg, ex);
    }
}

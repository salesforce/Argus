/*
 * Copyright (c) 2016, Salesforce.com, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
	 
package com.salesforce.dva.argus.util;

import java.util.*;

/**
 * An object representing a command line option.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class Option {

    //~ Static fields/initializers *******************************************************************************************************************

    /** Indicates the option type is not known. */
    public static final int UNKNOWN = -1;

    /** Indicates that a list option has an unconstrained argument list length. */
    public static final int UNLIMITED_LENGTH = -1;

    //~ Instance fields ******************************************************************************************************************************

    /** Indicates the length of the list of values associated with this option. */
    private final int len;

    /** The name of this option. */
    private final String name;

    /** The option type. Must be one of FLAG_TYPE, OPTION_TYPE, or LIST_TYPE */
    private final Type type;

    /** The values associated with this option. */
    private String value;

    /** The description of the option used to display usage. */
    private final String description;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new Option object.
     *
     * @param  type         The option type.
     * @param  name         The name of the option
     * @param  numvalues    The number of values associated with this option which must be greater or equal to 0 or UNLIMITED_LENGTH to indicate an
     *                      unbounded list of values
     * @param  description  The description of the option used to display usage
     */
    Option(Type type, String name, int numvalues, String description) {
        this.type = type;
        this.name = name;
        this.len = numvalues;
        this.description = description;
        this.value = "";
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Creates a new flag type option. These types of options are used to indicate some application switch is either on or off.
     *
     * @param   name         The name of the option
     * @param   description  The description of the option used to display usage
     *
     * @return  The option
     */
    public static Option createFlag(String name, String description) {
        return new Option(Type.FLAG, name, 0, description);
    }

    /**
     * Creates a new list type option. These types of options are used to supply lists of data such as filenames as command line arguments
     *
     * @param   description  The description of the option used to display usage
     *
     * @return  The option
     */
    public static Option createList(String description) {
        return new Option(Type.LIST, "", UNLIMITED_LENGTH, description);
    }

    /**
     * Returns a named option having one value. An example may be a logfile option for a program.
     *
     * <p>program -log &lt;logfile&gt;</p>
     *
     * <p>In this case the -log option has a length of one value of &lt;logfile&gt;.</p>
     *
     * @param   name         The name of the option.
     * @param   description  The description of the option used to display usage
     *
     * @return  The option.
     */
    public static Option createOption(String name, String description) {
        return new Option(Type.OPTION, name, 1, description);
    }

    /**
     * Returns a named option having a finite number of values. This is used to supply a finite list of arguments associated with a named option. An
     * example may be a 'files' option for a diff program.
     *
     * <p>diff -files &lt;file1&gt; &lt;file2&gt;</p>
     *
     * <p>In this case the -files option has a length of two values of &lt;file1&gt; and &lt;file2&gt;</p>
     *
     * @param   name         The name of the option
     * @param   numvalues    The number of values associated with the option.
     * @param   description  The description of the option used to display usage
     *
     * @return  The option
     */
    public static Option createOption(String name, int numvalues, String description) {
        return new Option(Type.OPTION, name, numvalues, description);
    }

    /**
     * Returns a list type application option if one exists in the list provided.
     *
     * @param   options  The options to search through.
     *
     * @return  The first list type application option or null if none are found.
     */
    public static Option findListOption(Option[] options) {
        for (int i = 0; i < options.length; i++) {
            if (options[i].getType() == Type.LIST) {
                return options[i];
            }
        }
        return null;
    }

    /**
     * Returns an option with the given name.
     *
     * @param   name     The option name to search for
     * @param   options  The list of options to search through
     *
     * @return  The named option from the list or null if it doesn't exist
     */
    public static Option findOption(String name, Option[] options) {
        for (int i = 0; i < options.length; i++) {
            if (options[i].getName().equals(name)) {
                return options[i];
            }
        }
        return null;
    }

    /**
     * Parses the command line arguments of an application and compares those options against a list of expected options.
     *
     * @param   args       The command line arguments to parse
     * @param   templates  The template list of application options.
     *
     * @return  A list of options that are present on the command line, are valid for the application and have data populated in them.
     *
     * @throws  IllegalArgumentException  If an error in processing occurs.
     */
    public static Option[] parseCLArgs(String[] args, Option[] templates) {
        int i = 0;
        List<Option> options = new ArrayList<Option>(args.length);

        try {
            while (i < args.length) {
                String name = args[i++];
                Option template = findTemplate(name, templates);
                StringBuilder values = new StringBuilder();

                if (!Type.LIST.equals(template.getType())) {
                    for (int j = 0; j < template.getLen(); j++) {
                        values.append(args[i++]).append(" ");
                    }
                } else if (hasMoreFlags(args, --i, templates)) {
                    throw new IllegalArgumentException();
                } else {
                    for (String arg : Arrays.copyOfRange(args, i, args.length)) {
                        values.append(arg).append(" ");
                    }
                    template.setValue(values.toString().trim());

                    Option[] result = new Option[options.size() + 1];

                    result = options.toArray(result);
                    result[result.length - 1] = template;
                    return result;
                }
                template.setValue(values.toString().trim());
                options.add(template);
            } // end while
            return options.toArray(new Option[options.size()]);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    } // end method parseCLArgs

    /**
     * Searches an array of option templates and returns an option matching the supplied name or null if no match is found.
     *
     * @param   name       The name of the option to search for
     * @param   templates  An array of option templates in which to search
     *
     * @return  An Option object matching the supplied option name or null if no match is found
     */
    private static Option findTemplate(String name, Option[] templates) {
        boolean listAllowed = false;
        Option listOption = null;

        for (int i = 0; i < templates.length; i++) {
            if (templates[i].getName().equals(name)) {
                return templates[i];
            }
            if (Type.LIST.equals(templates[i].getType())) {
                listAllowed = true;
                listOption = templates[i];
            }
        }
        if (listAllowed) {
            return listOption;
        }
        return null;
    }

    /**
     * Determines if a String array representation of option names contains an option whose name matches a FLAG_TYPE option in the supplied array of
     * Option templates. The search starts from the supplied starting index and ends with the last element in <code>args[]</code>
     *
     * @param   args       A String array containing option names
     * @param   start      The index of <code>args[]</code> at which to start comparing
     * @param   templates  A list of Option templates in which to search for a match against option names
     *
     * @return  True if an option name in <code>args[]</code> matches an option template of type FLAG_TYPE
     */
    private static boolean hasMoreFlags(String[] args, int start, Option[] templates) {
        for (int i = start; i < args.length; i++) {
            Option template = findTemplate(args[i], templates);

            if (((template == null) || (!Type.LIST.equals(template.getType())))) {
                return true;
            } else {
                if (i == (args.length - 1)) {
                    break;
                }
            }
        }
        return false;
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the description of this option.
     *
     * @return  The description of this option.
     */
    public String getDescription() {
        return this.description;
    }

    /**
     * Returns the number of data values associated with this option.
     *
     * @return  The number of data values associated with this option.
     */
    public int getLen() {
        return this.len;
    }

    /**
     * Returns the name of this option.
     *
     * @return  The name of this option.
     */
    public String getName() {
        return this.name;
    }

    /**
     * Returns the type of this option.
     *
     * @return  The type of this option.
     */
    public Type getType() {
        return this.type;
    }

    /**
     * Returns the value of this option as a string.
     *
     * @return  The value of this option.
     */
    public String getValue() {
        return this.value;
    }

    /**
     * Returns the value of this option as an array. This array will be empty for flag options, contain a single value for a simple option or a list
     * of values for list type options.
     *
     * @return  An array containing the option values.
     */
    public String[] getValues() {
        return value == null || value.isEmpty() ? new String[0] : len == 1 ? new String[] { value } : value.split("\\s+");
    }

    /**
     * Sets the value of this option.
     *
     * @param  value  The new value of the option.
     */
    public void setValue(String value) {
        this.value = value;
    }

    /**
     * Returns a string representation of the values associated with this option.
     *
     * @return  The values associated with this option.
     */
    @Override
    public String toString() {
        if (Type.LIST.equals(this.type)) {
            return ("INT_LIST" + ((value.length() != 0) ? (":" + value) : ("")));
        } else {
            return (name + ((value.length() != 0) ? (":" + value) : ("")));
        }
    }

    //~ Enums ****************************************************************************************************************************************

    /**
     * Represents the different option types.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    public enum Type {

        /** An option having consisting only of a key with no values. */
        FLAG,
        /** An option having a finite set of values. */
        OPTION,
        /** An option having an indefinite set of values. */
        LIST
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

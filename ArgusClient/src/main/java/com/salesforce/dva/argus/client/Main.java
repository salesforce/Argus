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
	 
package com.salesforce.dva.argus.client;

import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import com.salesforce.dva.argus.system.SystemMain;
import com.salesforce.dva.argus.util.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.salesforce.dva.argus.client.ClientType.COMMIT_METRICS;
import static com.salesforce.dva.argus.util.Option.findOption;

/**
 * Main command line remote executor client for executing Argus offline processing tasks. These tasks include asynchronous tasks that support
 * functionality that may be distributed or would put unnecessary load on the Argus core services running in resource limited containers such as the
 * web service layer.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class Main {

    //~ Static fields/initializers *******************************************************************************************************************

    private static final Logger LOGGER;
    private static final Option HELP_OPTION;
    private static final Option TYPE_OPTION;
    private static final Option INSTALL_OPTION;
    private static final Option CONFIG_OPTION;
    private static final Option[] TEMPLATES;

    static {
        HELP_OPTION = Option.createFlag("-h", "Display the usage and available collector types.");
        TYPE_OPTION = Option.createOption("-t",
            "[ COMMIT_METRICS | COMMIT_ANNOTATIONS | ALERT | COMMIT_SCHEMA ] Specifies the type of client to create. Default is COMMIT_METRICS");
        INSTALL_OPTION = Option.createOption("-i", "<path> Specifies a file location to store a configuration created interactively.");
        CONFIG_OPTION = Option.createOption("-f", "<path> Specifies the configuration file to use.");
        TEMPLATES = new Option[] { HELP_OPTION, TYPE_OPTION, INSTALL_OPTION, CONFIG_OPTION };
        LOGGER = LoggerFactory.getLogger(Main.class.getPackage().getName());
    }

    //~ Instance fields ******************************************************************************************************************************

    SystemMain _system;
    final AtomicInteger _jobCounter;

    //~ Constructors *********************************************************************************************************************************

    /** Creates a new Main object. */
    Main() {
        this(null);
    }

    /**
     * Creates a new Main object with a specific configuration used to facilitate unit testing.
     *
     * @param   config  The configuration for the collector.
     *
     * @throws  SystemException  If an error occurs.
     */
    Main(Properties config) {
        _jobCounter = new AtomicInteger(0);
        try {
            _system = SystemMain.getInstance(config);
            _system.start();
        } catch (Exception ex) {
            throw new SystemException("Could not create client.", ex);
        }
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * The main invocation method.
     *
     * @param   args  The command line arguments passed to the invocation. Cannot be null, but may be empty.
     *
     * @throws  IOException  If the configuration file cannot be located.
     */
    public static void main(String[] args) throws IOException {
        main(args, System.out);
    }

    /**
     * The main invocation method.
     *
     * @param   args  The command line arguments passed to the invocation. Cannot be null but may be empty.
     * @param   out   A print stream to facilitate unit testing. Cannot be null.
     *
     * @throws  IOException  If the configuration file was specified but could not be located.
     */
    static void main(String[] args, PrintStream out) throws IOException {
        try {
            Main main = null;
            Option[] options = Option.parseCLArgs(args, TEMPLATES);
            Option helpOption = (options == null) ? null : findOption(HELP_OPTION.getName(), options);
            Option installOption = (options == null) ? null : findOption(INSTALL_OPTION.getName(), options);
            Option configOption = (options == null) ? null : findOption(CONFIG_OPTION.getName(), options);
            Option typeOption = (options == null) ? null : findOption(TYPE_OPTION.getName(), options);

            if (helpOption != null) {
                out.println(usage());
            } else if (installOption != null) {
                SystemConfiguration.generateConfiguration(System.in, out, new File(installOption.getValue()));
            } else if (configOption != null) {
                FileInputStream fis = null;
                Properties props = new Properties();

                try {
                    fis = new FileInputStream(configOption.getValue());
                    props.load(fis);
                } finally {
                    if (fis != null) {
                        fis.close();
                    }
                }
                main = new Main(props);
            } else {
                main = new Main();
            }
            if (main != null) {
                ClientType type;

                try {
                    type = typeOption == null ? COMMIT_METRICS : ClientType.valueOf(typeOption.getValue());
                } catch (Exception ex) {
                    type = COMMIT_METRICS;
                }

                final Thread mainThread = Thread.currentThread();

                Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {

                            @Override
                            public void run() {
                                mainThread.interrupt();
                                try {
                                    mainThread.join();
                                } catch (InterruptedException ex) {
                                    LOGGER.warn("Interrupted while shutting down.  Giving up.");
                                }
                            }
                        }));
                main.invoke(type);
            }
        } catch (IllegalArgumentException ex) {
            out.println(usage());
        } // end try-catch
    }

    private static String usage() throws IOException {
        StringBuilder sb = new StringBuilder();

        try(BufferedReader bis = new BufferedReader(new InputStreamReader(Main.class.getResourceAsStream("usage.txt")))) {
            String line;

            while ((line = bis.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        for (Option option : TEMPLATES) {
            sb.append(String.format("\t%1$-3s%2$s", option.getName(), option.getDescription())).append("\n");
        }
        return sb.toString();
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the processed job count.
     *
     * @return  The number of jobs which have been processed by this client. Will never be negative.
     */
    int getJobCount() {
        return _jobCounter.get();
    }

    /**
     * This method creates multiple threads to dequeue messages from message queue and push into TSDB. One thread is created for every type. Depending on
     * message queue connection count, multiple threads may be created for Metric type
     *
     * @param   clientType  The type of client to invoke.
     *
     * @throws  SystemException  if an error occurs
     */
    void invoke(ClientType clientType) {
        try {
            LOGGER.info("Starting service.");

            ExecutorService service = ClientServiceFactory.startClientService(_system, clientType, _jobCounter);

            LOGGER.info("Service started.");

            Thread currentThread = Thread.currentThread();

            while (!currentThread.isInterrupted()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                    currentThread.interrupt();
                    break;
                }
            }
            LOGGER.info("Stopping service.");
            service.shutdownNow();
            try {
                if (!service.awaitTermination(60000, TimeUnit.MILLISECONDS)) {
                    LOGGER.warn("Shutdown timed out after 60 seconds.  Exiting.");
                }
            } catch (InterruptedException iex) {
                LOGGER.warn("Forcing shutdown.");
            }
            LOGGER.info("Service stopped.");
        } catch (Exception ex) {
            throw new SystemException("There was a problem invoking the committer.", ex);
        } finally {
            if (_system != null) {
                _system.stop();
            }
            LOGGER.info("Finished");
        } // end try-catch-finally
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

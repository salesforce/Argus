/**
 *
 * Copyright 2013, salesforce.com
 * All rights reserved
 * Company confidential
 */
package com.salesforce.perfeng.akc.consumer;

import java.io.IOException;
import java.io.InputStream;
import java.security.Security;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Properties;
import java.util.ArrayList;
import java.util.List;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import com.salesforce.dva.argus.system.SystemException;
import com.salesforce.dva.argus.system.SystemMain;
import com.salesforce.dva.argus.service.TSDBService;

import com.salesforce.perfeng.akc.AKCConfiguration;
import com.salesforce.sds.keystore.DynamicKeyStoreProvider;
import com.salesforce.sds.pki.utils.BouncyIntegration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.atomic.AtomicInteger;


/**
 *
 * @author Colby Guan (colbert.guan@salesforce.com)
 * @author Bhinav Sura (bhinav.sura@salesforce.com)
 *
 *	Main class. This class manages the entire kakfa consumer.
 *	It spawns a Site Manager thread for each site(zookeeper connect) i.e. asg, sjl, tyo, chi1, chi2, was1, was2 etc.
 *
 */
public class KafkaConsumerManager {
	private static final Logger LOGGER = LoggerFactory.getLogger(KafkaConsumerManager.class);

	private final ExecutorService siteExecutor;
	private final String bootstrapServers;

	SystemMain system;

	static {
		// Adding akc.ssl.enablefips flag to be able to enable FIPS if necessary
		final Boolean enableFIPS = Boolean.getBoolean("akc.ssl.enablefips");
		if (Boolean.TRUE.compareTo(enableFIPS) == 0) {
			LOGGER.info("BC init FIPS");
			BouncyIntegration.init();
		} else {
			LOGGER.info("BC init without FIPS");
			BouncyIntegration.initWithoutFips();
		}

		if (Security.getProvider("PKI-KS") == null) {
			LOGGER.info("Initializing Dynamic Keystore Provider");
			Security.addProvider(new DynamicKeyStoreProvider());
		}
	}

	public KafkaConsumerManager(String bootstrapServers) {
		this.bootstrapServers = bootstrapServers;
		this.siteExecutor = Executors.newFixedThreadPool(1);

		configureAKCLogger();
		system = SystemMain.getInstance();
		system.start();
		system.getServiceFactory().getMonitorService().startRecordingCounters();
	}

	private void configureAKCLogger() {
		InputStream is = null;

		try {
			String rootName = ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME;
			ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(rootName);
			LoggerContext context = root.getLoggerContext();
			JoranConfigurator configurator = new JoranConfigurator();

			is = getClass().getResourceAsStream("/META-INF/logback.xml");
			context.reset();
			configurator.setContext(context);
			configurator.doConfigure(is);
			root.setLevel(Level.ERROR);
		} catch (JoranException ex) {
			throw new SystemException(ex);
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException ex) {
					assert false : "This should never occur.";
				}
			}
		}
	}

	public static void main(String[] args) throws Exception {
		LOGGER.info("Instantiating Argus Ajna Consumer...");

		/* ConsumerType loaded from:
		 * - if CLI argument empty: use akc.config file
		 * - if CLI argument present: override akc.config and use CLI
		 * - Both CLI argument and akc.config default to METRICS ConsumerType
		 */
		ConsumerType type = ConsumerType.METRICS;
		if(args.length == 0){
			type = ConsumerType.valueOf(AKCConfiguration.getParameter(AKCConfiguration.Parameter.CONSUMER_TYPE));
			LOGGER.info("CLI argument not found. Using consumer.type property from AKCConfiguration: " + type);
		} else {
			try{
				type = ConsumerType.valueOf(args[0]);
			} catch (Exception ex){
				LOGGER.error("Exception reading CLI ConsumerType argument; default to METRICS: " + ex);
				type = ConsumerType.METRICS;
			} finally {
				AKCConfiguration.setConsumerType(type);
				LOGGER.info("CLI argument found. Setting consumer.type property in AKCConfiguration to " + type);
			}
		}
		String bootstrapServers = AKCConfiguration.getParameter(AKCConfiguration.Parameter.BOOTSTRAP_SERVERS);
		final KafkaConsumerManager manager = new KafkaConsumerManager(bootstrapServers);
		manager.init(type);
	}

	public void init(ConsumerType consumerType) throws Exception {
		LOGGER.info("Creating a siteExecutor thread for site: " +
                            bootstrapServers + " for consumer type =" + consumerType);
                Properties props = ConsumerConfigFactory.createConsumerConfig();
                String amurThreadName = consumerType + "-amur-runner";
                AmurRunnerThreadFactory amurRunner = new AmurRunnerThreadFactory(amurThreadName,
                                                                                 Thread.NORM_PRIORITY);
                ExecutorService execService = Executors.newCachedThreadPool(amurRunner);
                String clientId = props.getProperty("client.id");
                int numStreams = Integer.parseInt(props.getProperty(AKCConfiguration.Parameter.NUM_STREAMS.getKeyName()));
                List<AmurKafkaRunner> amurRunners = new ArrayList<>();
                for (int i=0; i < numStreams; i++) {
                    props.setProperty("client.id", clientId + "-stream" + i);
                    AmurKafkaRunner akr = new AmurKafkaRunner<byte[], byte[]>(consumerType,
                                                                              props,
                                                                              system,
                                                                              new AtomicInteger(0),
                                                                              AjnaConsumerTask.class);
                    amurRunners.add(akr);
                }

                TSDBService tsdbService = system.getServiceFactory().getTSDBService();

                OneConsumerPerThreadSiteManager oneConsumer = new OneConsumerPerThreadSiteManager(system,
                                                                                                  props,
                                                                                                  execService,
                                                                                                  amurRunners,
                                                                                                  tsdbService);
		siteExecutor.execute(oneConsumer);

		Thread currentThread = Thread.currentThread();

		while (!currentThread.isInterrupted()) {
			try {
				Thread.sleep(500);
			} catch (InterruptedException ex) {
				currentThread.interrupt();
				break;
			}
		}
		siteExecutor.shutdownNow();
		try {
			if (!siteExecutor.awaitTermination(60000, TimeUnit.MILLISECONDS)) {
				LOGGER.warn("Shutdown timed out after 60 seconds.  Exiting.");
			}
		} catch (InterruptedException iex) {
			LOGGER.warn("Forcing shutdown.");
		}
		LOGGER.info("Service stopped.");
		if (system != null) {
			system.stop();
		}
	}
}

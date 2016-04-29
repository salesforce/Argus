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
	 
package com.salesforce.dva.argus.service.warden;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.Policy;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.service.AnnotationService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.WaaSService;
import com.salesforce.dva.argus.service.alert.DefaultAlertService.NotificationContext;
import com.salesforce.dva.argus.service.alert.notifier.AuditNotifier;
import com.salesforce.dva.argus.service.alert.notifier.DefaultNotifier;
import com.salesforce.dva.argus.system.SystemConfiguration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import javax.persistence.EntityManager;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Default implementation of the warden as a service notifier. This WaaS
 * specific implementation must send out warden event for the user associated
 * with the WaaS alert for the right service, if the policy is violated as
 * determined by the trigger events. Additionally, the notifier must create an
 * annotation on the specific time series specified in the alert expression
 * which indicates the time the policy was violated.
 *
 * @author Ruofan Zhang (rzhang@salesforce.com)
 */
public  class WaaSNotifier extends DefaultNotifier {

    //~ Static fields/initializers *******************************************************************************************************************

	private static final String ANNOTATION_SOURCE = "ARGUS-WAAS";
    private static final String ANNOTATION_TYPE = "WAAS";

    //~ Instance fields ******************************************************************************************************************************

    @Inject
    private Provider<EntityManager> emf;
    protected final WaaSService _waaSService;
    private final TSDBService _tsdbService;
    private final AnnotationService _annotationService;
    private final SystemConfiguration _config;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new WaaSNotifier object.
     *
     * @param  metricService      The metric service. Cannot be null.
     * @param  annotationService  The annotation service. Cannot be null.
     * @param  waaSService        The WaaS service. Cannot be null.
     * @param  tsdbService        The tsdb service instance to use. Cannot be null.
     * @param  config             The system configuration. Cannot be null.
     */
    @Inject
    protected WaaSNotifier(MetricService metricService, AnnotationService annotationService, WaaSService waaSService, TSDBService tsdbService,
        SystemConfiguration config) {
        super(metricService, annotationService, config);
        requireArgument(waaSService != null, "Warden service cannot be null.");
        requireArgument(tsdbService != null, "TSDB service cannot be null.");
        requireArgument(annotationService != null, "Annotation service cannot be null.");
        requireArgument(config != null, "The configuration cannot be null.");
        _waaSService = waaSService;
        _tsdbService = tsdbService;
        _annotationService = annotationService;
        _config = config;
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public String getName() {
        return WaaSNotifier.class.getName();
    }

    @Override
    protected void sendAdditionalNotification(NotificationContext context){
    	List<String> nameAndService = _parseMetricExpression(context);
    	Policy policy = _waaSService.getPolicy(nameAndService.get(0),nameAndService.get(1));
    	_waaSService.suspendUser(getWaaSUser(context.getAlert().getName()).getUserName(), policy);
    	//TODO:send out warden event, where socket communication happens
        addAnnotationSuspendedUser(context, policy);
    }

    private List<String> _parseMetricExpression(NotificationContext context) {
		
    	String metricExpression = context.getAlert().getExpression();
    	
		int policyNameBeginIndex = metricExpression.lastIndexOf(":") + 1;
        int policyNameEndIndex = metricExpression.lastIndexOf("{");
        String name = metricExpression.substring(policyNameBeginIndex, policyNameEndIndex);
		int scopeBeginIndex = metricExpression.indexOf(":") + 1;
		int scopeEndIndex = metricExpression.lastIndexOf(":");
		String scope = metricExpression.substring(scopeBeginIndex, scopeEndIndex);
		String service = scope.contains(".") ? scope.substring(0, scope.indexOf('.')): scope;
		
		return Arrays.asList(name, service);
	}

	/**
     * Add annotation for user suspension to the <tt>triggers.warden</tt> metric..
     *
     * @param  context    The notification context.  Cannot be null.
     * @param  subSystem  The subsystem for which the user is being suspended.  Cannot be null.
     */
    protected void addAnnotationSuspendedUser(NotificationContext context, Policy policy) {
        Alert alert = context.getAlert();
        PrincipalUser waaSUser = getWaaSUser(alert.getName());
        Metric metric = null;

        Map<Long, String> datapoints = new HashMap<>();

        String scope = policy.getSubSystem() == null ? policy.getService() : policy.getService() + "." + policy.getSubSystem();
        metric = new Metric(scope, policy.getName());
        metric.setTag("user", waaSUser.getUserName());
        datapoints.put(context.getTriggerFiredTime(), "1");
        metric.setDatapoints(datapoints);
        _tsdbService.putMetrics(Arrays.asList(new Metric[] { metric }));

        Annotation annotation = new Annotation(ANNOTATION_SOURCE, waaSUser.getUserName(), ANNOTATION_TYPE, scope,
            policy.getName(), context.getTriggerFiredTime());
        Map<String, String> fields = new TreeMap<>();

        fields.put("Suspended from service", policy.getService());
        fields.put("Alert Name", alert.getName());
        fields.put("Notification Name", context.getNotification().getName());
        fields.put("Trigger Name", context.getTrigger().getName());
        annotation.setFields(fields);
        _annotationService.updateAnnotation(alert.getOwner(), annotation);
    }

    /**
     * From WaaS alert name, de-constructs the user for whom this warden alert is associated with.
     *
     * @param   waaSAlertName  Name of warden alert
     *
     * @return  User associated with the warden alert
     */
    protected PrincipalUser getWaaSUser(String waaSAlertName) {
        assert (waaSAlertName != null) : "Warden alert name cannot be null.";

        int beginIndex = waaSAlertName.indexOf("-") + 1;
        int endIndex = waaSAlertName.lastIndexOf("-");

        return PrincipalUser.findByUserName(emf.get(), waaSAlertName.substring(beginIndex, endIndex));
    }

    /** 
     * Warden triggers are not stateful.  This method implementation is empty.
     * 
     * @param notificationContext The notification context. 
     */
    @Override
    public void clearNotification(NotificationContext notificationContext) { }

    /** No additional action needs to be taken for clearing warden notifications as they are not stateful.  This implementation is empty. */
    @Override
    protected void clearAdditionalNotification(NotificationContext context) { }
    
    @Override
    public Properties getNotifierProperties() {
            Properties notifierProps= super.getNotifierProperties();

            for(Property property:Property.values()){
                    notifierProps.put(property.getName(), property.getDefaultValue());
            }
            return notifierProps;
    }
    
  //~ Enums ****************************************************************************************************************************************

    /**
     * The enumeration of implementation specific configuration properties.
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    public enum Property {

        /** The alert URL template to use in notifications. */
        WARDEN_ALERT_URL_TEMPLATE("notifier.property.alert.alerturl.template", "http://localhost:8080/argus/alertId"),
        /** The metric URL template to use in notifications. */
        WARDEN_METRIC_URL_TEMPLATE("notifier.property.alert.metricurl.template", "http://localhost:8080/argus/metrics");
        
        private final String _name;
        private final String _defaultValue;

        private Property(String name, String defaultValue) {
            _name = name;
            _defaultValue = defaultValue;
        }

        /**
         * Returns the property name.
         *
         * @return  The property name.
         */
        public String getName() {
            return _name;
        }

        /**
         * Returns the default value.
         *
         * @return  The default value.
         */
        public String getDefaultValue() {
            return _defaultValue;
        }
    }
    
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

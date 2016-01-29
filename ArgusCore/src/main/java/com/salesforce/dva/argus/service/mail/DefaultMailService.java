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
	 
package com.salesforce.dva.argus.service.mail;

import com.google.inject.Inject;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.MailService;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import org.slf4j.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;
import static com.salesforce.dva.argus.system.SystemAssert.requireState;

/**
 * Default implementation of the email service.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class DefaultMailService extends DefaultService implements MailService {

    //~ Instance fields ******************************************************************************************************************************

    @SLF4JTypeListener.InjectLogger
    private Logger _logger;
    private final SystemConfiguration _config;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new DefaultMailService object.
     *
     * @param  config  The system configuration. Cannot be null.
     */
    @Inject
    public DefaultMailService(SystemConfiguration config) {
    	super(config);
        requireArgument(config != null, "Configuration cannot be null.");
        _config = config;
    }

    //~ Methods **************************************************************************************************************************************

    private Properties getMailProperties() {
        Properties result = new Properties();

        result.put("mail.smtp.host", _config.getValue(Property.EMAIL_SMTP_HOST.getName(), Property.EMAIL_SMTP_HOST.getDefaultValue()));
        result.put("mail.smtp.auth", _config.getValue(Property.EMAIL_SMTP_AUTH.getName(), Property.EMAIL_SMTP_AUTH.getDefaultValue()));
        result.put("mail.smtp.starttls.enable",
            _config.getValue(Property.EMAIL_SMTP_STARTTTLS.getName(), Property.EMAIL_SMTP_STARTTTLS.getDefaultValue()));
        return result;
    }

    @Override
    public void sendMessage(Set<String> to, String subject, String body, String contentType, Priority priority) {
        requireState(!isDisposed(), "Cannot call methods on a disposed service.");
        requireArgument(to != null && !to.isEmpty(), "Recipients cannot be null or empty.");
        if (contentType == null || contentType.isEmpty()) {
            contentType = "text; charset=utf-8";
        }
        if (priority == null) {
            priority = Priority.NORMAL;
        }
        if (Boolean.valueOf(_config.getValue(com.salesforce.dva.argus.system.SystemConfiguration.Property.EMAIL_ENABLED))) {
            try {
                Session session = Session.getInstance(getMailProperties());
                MimeMessage message = new MimeMessage(session);

                message.setFrom(new InternetAddress(_config.getValue(com.salesforce.dva.argus.system.SystemConfiguration.Property.ADMIN_EMAIL)));
                message.setSubject(subject);
                message.setRecipients(Message.RecipientType.TO, getEmailToAddresses(to));
                message.addHeader("X-Priority", String.valueOf(priority.getXPriority()));

                BodyPart messageBodyPart1 = new MimeBodyPart();

                messageBodyPart1.setContent(body, contentType);

                Multipart multipart = new MimeMultipart();

                multipart.addBodyPart(messageBodyPart1);
                message.setContent(multipart);
                Transport.send(message);
                _logger.info("Sent email having subject '{}' to {}.", subject, to);
            } catch (Exception ex) {
                throw new SystemException("Failed to send an email notification.", ex);
            }
        } else {
            _logger.info("Sending email is disabled.  Not sending email having subject '{}' to {}.", subject, to);
        }
    }

    private Address[] getEmailToAddresses(Set<String> recipientEmailAddresses) throws AddressException {
        List<Address> list = new ArrayList<>();

        for (String emailAddress : recipientEmailAddresses) {
            list.add(new InternetAddress(emailAddress));
        }
        return list.toArray(new Address[list.size()]);
    }
    
    @Override
    public Properties getServiceProperties() {
            Properties serviceProps= new Properties();

            for(Property property:Property.values()){
                    serviceProps.put(property.getName(), property.getDefaultValue());
            }
            return serviceProps;
    }

    //~ Enums ****************************************************************************************************************************************

    /**
     * The implementation specific system configuration properties.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    public enum Property {

        /** The SMTP endpoint URL. */
        EMAIL_SMTP_HOST("service.property.mail.smtp.host", "test.smtp.net"),
        /** Indicates if SMTP authentication is required.  Default is false. */
        EMAIL_SMTP_AUTH("service.property.mail.smtp.auth", "false"),
        /** Indicates if TLS should be used. */
        EMAIL_SMTP_STARTTTLS("service.property.mail.smtp.starttls.enable", "false");

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
         * Returns the default property value.
         *
         * @return  The default property value.
         */
        public String getDefaultValue() {
            return _defaultValue;
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

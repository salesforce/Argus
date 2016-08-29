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
	 
package com.salesforce.dva.argus.service.auth;

import com.google.inject.Inject;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.service.AuthService;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.UserService;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import org.slf4j.Logger;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Default implementation of Audit service.
 *
 * @author  Raj Sarkapally (rsarkapally@salesforce.com)
 */
public class LDAPAuthService extends DefaultService implements AuthService {

    //~ Static fields/initializers *******************************************************************************************************************

    private static final String EMAIL_REGEX = "^[_A-Za-z0-9-\\+]+(\\.[_A-Za-z0-9-]+)*(@[_A-Za-z0-9-]+(\\.[_A-Za-z0-9]+)*)?$";

    //~ Instance fields ******************************************************************************************************************************

    @SLF4JTypeListener.InjectLogger
    private Logger _logger;
    private final UserService _userService;
    private final SystemConfiguration _config;
    private final MonitorService _monitorService;
    private final UserCountCache _monthlyUsers;
    private final UserCountCache _dailyUsers;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new DefaultAuditService object.
     *
     * @param  config          The system configuration. Cannot be null.
     * @param  userService     The user service. Cannot be null.
     * @param  monitorService  The monitor service. Cannot be null.
     */
    @Inject
    LDAPAuthService(SystemConfiguration config, UserService userService, MonitorService monitorService) {
    	super(config);
        requireArgument(config != null, "The system configuration cannot be null.");
        requireArgument(userService != null, "The user service cannot be null.");
        requireArgument(monitorService != null, "The monitor service cannot be null.");
        _userService = userService;
        _config = config;
        _monitorService = monitorService;
        _dailyUsers = new UserCountCache(86400000L);
        _monthlyUsers = new UserCountCache(2592000000L);
    }

    //~ Methods **************************************************************************************************************************************

    static boolean _isUsernameValid(String username) {
        Matcher matcher = Pattern.compile(EMAIL_REGEX).matcher(username);

        return matcher.matches();
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public PrincipalUser getUser(String username, String password) {
        requireNotDisposed();
        requireArgument(_isUsernameValid(username), MessageFormat.format("Invalid username: {0}", username));

        PrincipalUser result = null;
        String userDn = _findDNForUser(username);

        if (userDn != null && password != null) {
            result = _findPrincipalUser(userDn, username, password);
        }
        return result;
    }

    private SearchResult _findAccountByAccountName(String searchDn, String searchPwd, String accountName) {
        Hashtable<String, Object> env = new Hashtable<>();

        env.put(Context.SECURITY_AUTHENTICATION, _config.getValue(Property.LDAP_AUTHTYPE.getName(), Property.LDAP_AUTHTYPE.getDefaultValue()));
        env.put(Context.PROVIDER_URL, _config.getValue(Property.LDAP_ENDPOINT.getName(), Property.LDAP_ENDPOINT.getDefaultValue()));
        env.put(Context.SECURITY_PRINCIPAL, searchDn);
        env.put(Context.SECURITY_CREDENTIALS, searchPwd);
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");

        SearchResult searchResult = null;

        try {
            DirContext ctx = new InitialDirContext(env);
            String searchFilter = "(" + _config.getValue(Property.LDAP_USERNAMEFIELD.getName(), Property.LDAP_USERNAMEFIELD.getDefaultValue()) + "=" +
                accountName + ")";
            String[] ldapSearchBase = _config.getValue(Property.LDAP_SEARCHBASE.getName(), Property.LDAP_SEARCHBASE.getDefaultValue()).split(":");

            for (String searchBase : ldapSearchBase) {
                SearchControls searchControls = new SearchControls();

                searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);

                NamingEnumeration<SearchResult> results = ctx.search(searchBase, searchFilter, searchControls);

                if (results.hasMoreElements()) {
                    searchResult = SearchResult.class.cast(results.nextElement());
                    break;
                }
            }
        } catch (NamingException ex) {
            throw new SystemException(ex);
        }
        return searchResult;
    }

    private String _findDNForUser(String username) {
        String searchDn = _config.getValue(Property.LDAP_SEARCHDN.getName(), Property.LDAP_SEARCHDN.getDefaultValue());
        String searchPwd = _config.getValue(Property.LDAP_SEARCHPWD.getName(), Property.LDAP_SEARCHPWD.getDefaultValue());
        SearchResult rs = _findAccountByAccountName(searchDn, searchPwd, username);
        String result = null;

        if (rs != null) {
            try {
                Attribute attribute = rs.getAttributes().get("distinguishedName");

                result = String.valueOf(attribute.get());
            } catch (Exception ex) {
                throw new SystemException(ex);
            }
        }
        return result;
    }

    private PrincipalUser _findPrincipalUser(String userDn, String username, String password) {
        SearchResult rs = _findAccountByAccountName(userDn, password, username);

        if (rs != null) {
            PrincipalUser result = _userService.findUserByUsername(username);

            if (result == null) {
                Attributes attributes = rs.getAttributes();
                Attribute mail = attributes.get("mail");
                String email = username;

                if (mail != null) {
                    try {
                        email = String.valueOf(mail.get());
                    } catch (Exception ex) {
                        _logger.warn("Failed to retrieve mail address for {}.", username);
                    }
                }
                result = new PrincipalUser(_userService.findAdminUser(), username, email);
                result = _userService.updateUser(result);
            }
            _dailyUsers.put(username, System.currentTimeMillis());
            _monthlyUsers.put(username, System.currentTimeMillis());
            _monitorService.updateCounter(MonitorService.Counter.DAILY_USERS, _dailyUsers.size(), new HashMap<>(0));
            _monitorService.updateCounter(MonitorService.Counter.MONTHLY_USERS, _monthlyUsers.size(), new HashMap<>(0));
            _monitorService.updateCounter(MonitorService.Counter.UNIQUE_USERS, _userService.getUniqueUserCount(), new HashMap<>(0));
            return result;
        } else {
            return null;
        }
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
     * Enumerates the implementation specific properties for LDAP authentication.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    public enum Property {

        /** The LDAP endpoint. */
        LDAP_ENDPOINT("service.property.auth.ldap.endpoint", "ldaps://ldaps.test.com:123"),
        /** The LDAP search base. */
        LDAP_SEARCHBASE("service.property.auth.ldap.searchbase", "YYYY"),
        /** The distinguished name to be used for LDAP search. */
        LDAP_SEARCHDN("service.property.auth.ldap.searchdn", "XXXX"),
        /** The password to bind to the LDAP service. */
        LDAP_SEARCHPWD("service.property.auth.ldap.searchpwd", "SECRET"),
        /** The LDAP authentication type. */
        LDAP_AUTHTYPE("service.property.auth.ldap.authtype", "simple"),
        /** The LDAP field which contains the principal user name. */
        LDAP_USERNAMEFIELD("service.property.auth.ldap.usernamefield", "test.user");

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
         * Returns the default value for the property.
         *
         * @return  The default value.
         */
        public String getDefaultValue() {
            return _defaultValue;
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

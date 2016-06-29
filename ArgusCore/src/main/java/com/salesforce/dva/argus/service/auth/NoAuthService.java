package com.salesforce.dva.argus.service.auth;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;

import com.google.inject.Inject;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.service.AuthService;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.UserService;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;

public class NoAuthService extends DefaultService implements AuthService {

	private final UserService _userService;
	private final MonitorService _monitorService;
	private final SystemConfiguration _config;
	
	/**
	 * Creates a new NoAuthService object.
	 *
	 * @param  config          The system configuration. Cannot be null.
	 * @param  userService     The user service. Cannot be null.
	 * @param  monitorService  The monitor service. Cannot be null.
	 */
	@Inject
	NoAuthService(SystemConfiguration config, UserService userService, MonitorService monitorService) {
		super(config);
		requireArgument(config != null, "The system configuration cannot be null.");
		requireArgument(userService != null, "The user service cannot be null.");
		requireArgument(monitorService != null, "The monitor service cannot be null.");
		_userService = userService;
		_monitorService = monitorService;
		_config = config;
	}

	//~ Methods **************************************************************************************************************************************
	@Override
	public PrincipalUser getUser(String username, String password) {
		requireNotDisposed();

		PrincipalUser result = _userService.findUserByUsername(username);
		String isPrivileged= _config.getValue(Property.IS_PRIVILEGED.getName(), Property.IS_PRIVILEGED.getDefaultValue());

		if(result == null){
			result = new PrincipalUser(_userService.findAdminUser(), username, username+"@gmail.com");
		}
		_monitorService.updateCounter(MonitorService.Counter.UNIQUE_USERS, _userService.getUniqueUserCount(), new HashMap<String, String>(0));

		try{
			Method method = PrincipalUser.class.getDeclaredMethod("setPrivileged", boolean.class);
			method.setAccessible(true);
			method.invoke(result, Boolean.parseBoolean(isPrivileged));

		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new SystemException("Failed to change privileged status.", e);
		}

		result = _userService.updateUser(result);
		return result;
	}

	/**
	 * Enumerates the implementation specific properties for NoAuth authentication.
	 *
	 * @author  Dilip Devaraj (ddevaraj@salesforce.com)
	 */
	public enum Property {
		/** Return if a user is privileged or not. */
		IS_PRIVILEGED("service.property.auth.noauthservice.privileged", "false");

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
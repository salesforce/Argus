package com.salesforce.dva.argus.service.auth;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

import com.google.inject.Inject;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.service.AuthService;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.UserService;
import com.salesforce.dva.argus.system.SystemConfiguration;

public class NoAuthService extends DefaultService implements AuthService {

	private final UserService _userService;
	
	@Inject
	NoAuthService(SystemConfiguration systemConfiguration, UserService userService) {
		super(systemConfiguration);
		requireArgument(userService != null, "The user service cannot be null.");
		_userService = userService;
	}

	@Override
	public PrincipalUser getUser(String username, String password) {
		return _userService.findAdminUser();
	}

}

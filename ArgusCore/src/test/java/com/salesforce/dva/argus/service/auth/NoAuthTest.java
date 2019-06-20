package com.salesforce.dva.argus.service.auth;

import com.salesforce.dva.argus.TestUtils;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.UserService;
import org.junit.Test;

import static org.junit.Assert.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NoAuthTest {

	@Test
	public void testNoAuthDoesNotThrow() {

		String userName = "user";

		PrincipalUser principalUser1 = new PrincipalUser(null, userName, userName);

		UserService mockUserService = mock(UserService.class);
		when(mockUserService.findUserByUsername(any())).thenReturn(principalUser1);
		when(mockUserService.updateUser(any())).thenReturn(principalUser1);

		MonitorService mockMonitorService = mock(MonitorService.class);

		NoAuthService authService = new NoAuthService(TestUtils.getConfiguration(), mockUserService, mockMonitorService);

		PrincipalUser principalUser2 = authService.getUser(userName, userName);

		assertEquals(principalUser2.getUserName(), userName);
	}
}

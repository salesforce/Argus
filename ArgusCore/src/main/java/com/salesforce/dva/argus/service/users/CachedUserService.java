package com.salesforce.dva.argus.service.users;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;
import static java.math.BigInteger.ZERO;

import java.io.IOException;
import java.math.BigInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.inject.Inject;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.CacheService;
import com.salesforce.dva.argus.service.NamedBinding;
import com.salesforce.dva.argus.service.UserService;
import com.salesforce.dva.argus.service.jpa.DefaultJPAService;
import com.salesforce.dva.argus.system.SystemConfiguration;

public class CachedUserService extends DefaultJPAService implements UserService {
	
	//TTL of 30 days 
	private static final int TTL_SECS = 30 * 24 * 60 * 60;
	
	private final Logger _logger = LoggerFactory.getLogger(getClass());
	private final CacheService _cacheService;
	private final UserService _defaultUserService;
	private ObjectMapper _mapper;
	
	/**
     * Creates a new CachedUserService object.
     *
     * @param  auditService  The audit service. Cannot be null.
     * @param  config 	 Service properties
	 * @param cacheService   The cache service
	 * @param userService	 The user service
     */
    @Inject
    public CachedUserService(AuditService auditService, SystemConfiguration config, CacheService cacheService, 
    		@NamedBinding UserService userService) {
        super(auditService, config);
        _cacheService = cacheService;
        _defaultUserService = userService;
        
        _mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(PrincipalUser.class, new PrincipalUser.Serializer());
        module.addDeserializer(PrincipalUser.class, new PrincipalUser.Deserializer());
        _mapper.registerModule(module);
    }
	
	@Override
	public PrincipalUser findUserByUsername(String userName) {
		requireNotDisposed();
		requireArgument(userName != null && !userName.trim().isEmpty(), "User name cannot be null or empty.");
		
		String cachedUser = _cacheService.get(userName);
		
		if(cachedUser != null) {
			try {
				PrincipalUser user = _mapper.readValue(cachedUser, PrincipalUser.class);
				return user;
			} catch (IOException e) {
				_logger.warn("Failed to deserialize user object retrieved from cache. Will get from persistent storage.");
			}
		}
		
		_logger.debug("User not found in cache. Will read from persistent storage.");
		PrincipalUser user = _defaultUserService.findUserByUsername(userName);
		if(user != null) {
			try {
				_cacheService.put(user.getUserName(), _mapper.writeValueAsString(user), TTL_SECS);
				_cacheService.put(user.getId().toString(), _mapper.writeValueAsString(user), TTL_SECS);
			} catch (JsonProcessingException e) {
				_logger.warn("Failed to serialize user object. User {} will not be cached.", user);
			}
		}
		
		return user;
	}

	@Override
	public PrincipalUser findUserByPrimaryKey(BigInteger id) {
		requireNotDisposed();
		requireArgument(id != null && id.compareTo(ZERO) > 0, "ID must be a positive non-zero value.");
		
		String cachedUser = _cacheService.get(id.toString());
		
		if(cachedUser != null) {
			try {
				PrincipalUser user = _mapper.readValue(cachedUser, PrincipalUser.class);
				return user;
			} catch (IOException e) {
				_logger.warn("Failed to deserialize user object retrieved from cache. Will get from persistent storage.");
			}
		}
		
		_logger.debug("User not found in cache. Will read from persistent storage.");
		PrincipalUser user = _defaultUserService.findUserByPrimaryKey(id);
		if(user != null) {
			try {
				_cacheService.put(user.getUserName(), _mapper.writeValueAsString(user), TTL_SECS);
				_cacheService.put(user.getId().toString(), _mapper.writeValueAsString(user), TTL_SECS);
			} catch (JsonProcessingException e) {
				_logger.warn("Failed to serialize user object. User {} will not be cached.", user);
			}
		}
		
		return user;
	}
	
	@Override
	public void deleteUser(PrincipalUser user) {
		requireNotDisposed();
		requireArgument(user != null && user.getId() != null && user.getId().compareTo(ZERO) > 0, "User cannot be null and must have a valid ID.");
		
		_cacheService.delete(user.getId().toString());
		
		if(user.getUserName() == null || user.getUserName().isEmpty()) {
			user = findUserByPrimaryKey(user.getId());
			_cacheService.delete(user.getUserName().toString());
		}
		_defaultUserService.deleteUser(user);
	}

	@Override
	public PrincipalUser updateUser(PrincipalUser user) {
		requireNotDisposed();
		requireArgument(user != null, "User cannot be null.");
		
		if(user.getId() != null) {
			_cacheService.delete(user.getId().toString());
		}

		if(user.getUserName() != null) {
			_cacheService.delete(user.getUserName().toString());
		}
		return _defaultUserService.updateUser(user);
	}

	@Override
	public PrincipalUser findAdminUser() {
		requireNotDisposed();
		
		return _defaultUserService.findAdminUser();
	}

	@Override
	public PrincipalUser findDefaultUser() {
		requireNotDisposed();
		
		return _defaultUserService.findDefaultUser();
	}

	@Override
	public long getUniqueUserCount() {
		requireNotDisposed();
		
		return _defaultUserService.getUniqueUserCount();
	}

}

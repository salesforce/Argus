package com.salesforce.dva.argus.service.oauth;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import com.salesforce.dva.argus.entity.OAuthAuthorizationCode;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.OAuthAuthorizationCodeService;
import com.salesforce.dva.argus.system.SystemConfiguration;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import javax.persistence.EntityManager;
import java.sql.Timestamp;
import java.util.List;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;
public class DefaultOAuthAuthorizationCodeService extends DefaultService implements OAuthAuthorizationCodeService {

    @SLF4JTypeListener.InjectLogger
    private Logger _logger;
    @Inject
    Provider<EntityManager> emf;

    @Inject
    public DefaultOAuthAuthorizationCodeService(SystemConfiguration _sysConfig) {
        super(_sysConfig);
    }

    @Transactional
    @Override
    public OAuthAuthorizationCode create(OAuthAuthorizationCode authCodeEntity) {
        requireNotDisposed();
        requireArgument(StringUtils.isNotBlank(authCodeEntity.getAuthorizationCode()), "authorization_code cannot be null or empty");
        requireArgument(StringUtils.isNotBlank(authCodeEntity.getState()), "state cannot be null or empty");
        requireArgument(StringUtils.isNotBlank(authCodeEntity.getRedirectUri()), "Redirect URI cannot be null or empty");
        requireArgument(authCodeEntity != null, "Cannot update a null authorization code entity");
        EntityManager em = emf.get();
        OAuthAuthorizationCode result = em.merge(authCodeEntity);
        em.flush();
        _logger.debug("Created OAuthAuthorizationCode to : {}", result);
        return result;
    }

    @Override
    @Transactional
    public OAuthAuthorizationCode findByCodeAndRedirectURI(String code, String uri) {
        requireNotDisposed();
        requireArgument(StringUtils.isNotBlank(code), "authorization_code name cannot be null or empty");
        requireArgument(StringUtils.isNotBlank(uri), "redirect_uri name cannot be null or empty");
        _logger.debug("Querying Authorization Code by authorization_code: {} and redirect_uri: {}", code, uri);

        OAuthAuthorizationCode result = OAuthAuthorizationCode.findByCodeAndRedirectURI(emf.get(), code, uri);
        _logger.debug("Query for Authorization Code row having authorization_code {} resulted in : {}", code, result);
        return result;
    }

    @Override
    @Transactional
    public OAuthAuthorizationCode findByCodeAndState(String code, String state) {
        requireNotDisposed();
        requireArgument(StringUtils.isNotBlank(code), "authorization_code name cannot be null or empty");
        requireArgument(StringUtils.isNotBlank(state), "state name cannot be null or empty");
        _logger.debug("Querying Authorization Code by authorization_code: {} and state: {}", code, state);

        OAuthAuthorizationCode result = OAuthAuthorizationCode.findByCodeAndState(emf.get(), code, state);
        _logger.debug("Query for Authorization Code row having authorization_code {} and state {} resulted in : {}", code, state, result);
        return result;
    }

    @Override
    @Transactional
    public int countByUserId(String userName) {
        requireNotDisposed();
        requireArgument(StringUtils.isNotBlank(userName), "userName cannot be null or empty");
        _logger.debug("Querying records by userName: {}", userName);

        int result = OAuthAuthorizationCode.findByUserId(emf.get(), userName);
        _logger.debug("Querying for count of records by userName:{} resulted in : {}",userName, result);
        return result;
    }

    @Override
    @Transactional
    public int deleteExpiredAuthCodesByUserName(Timestamp currentTime,String userName) {
        requireNotDisposed();
        _logger.debug("Deleting expired records by currentTime: {} and userName: {}", currentTime,userName);

        int result = OAuthAuthorizationCode.deleteByTimeStamp(emf.get(), currentTime,userName);
        _logger.debug("Query for deleting records by currentTime:{} and userName: {} resulted in : {} rows deleted",currentTime, userName,result);
        return result;
    }

    @Override
    @Transactional
    public int deleteByUserId(String userName) {
        requireNotDisposed();
        _logger.debug("Deleting expired records by userName: {}", userName);

        int result = OAuthAuthorizationCode.deleteByUserId(emf.get(), userName);
        _logger.debug("Query for deleting records by userName:{} resulted in : {} rows deleted",userName, result);
        return result;
    }

    @Override
    @Transactional
    public int updateExpiry(String code, Timestamp expires) {
        requireNotDisposed();
        requireArgument(StringUtils.isNotBlank(code), "authorization_code name cannot be null or empty");
        requireArgument(expires != null, "expires cannot be null or empty");
        _logger.debug("Updating Authorization Code expires for authorization_code: {} to: {}", code, expires);
        int result = OAuthAuthorizationCode.updateExpires(emf.get(), code, expires);
        emf.get().flush();
        _logger.debug("Query for Authorization Code row having authorization_code {} resulted in : {} rows updated", code, result);
        return result;
    }

    @Override
    @Transactional
    public int updateUserId(String code, String state, String userId) {
        requireNotDisposed();
        requireArgument(StringUtils.isNotBlank(code), "authorization_code cannot be null or empty");
        requireArgument(StringUtils.isNotBlank(state), "state cannot be null or empty");
        requireArgument(StringUtils.isNotBlank(userId), "user_id cannot be null or empty");
        _logger.debug("Updating Authorization Code user_id for authorization_code {} and state {} to: {}", code, state, userId);
        int result = OAuthAuthorizationCode.updateUserId(emf.get(), code, state, userId);
        emf.get().flush();
        _logger.debug("Query for Authorization Code row having authorization_code {} and state {} resulted in : {} rows updated", code, state, result);
        return result;
    }

}

/* Copyright (c) 2018, Salesforce.com, Inc.  All rights reserved. */
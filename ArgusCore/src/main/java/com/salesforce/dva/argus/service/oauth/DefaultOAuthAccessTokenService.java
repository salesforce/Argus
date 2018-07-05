package com.salesforce.dva.argus.service.oauth;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import com.salesforce.dva.argus.entity.OAuthAccessToken;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.OAuthAccessTokenService;
import com.salesforce.dva.argus.system.SystemConfiguration;
import org.slf4j.Logger;

import javax.persistence.EntityManager;
import java.sql.Timestamp;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

public class DefaultOAuthAccessTokenService extends DefaultService implements OAuthAccessTokenService {

    @SLF4JTypeListener.InjectLogger
    private Logger _logger;
    @Inject
    Provider<EntityManager> emf;

    @Inject
    public DefaultOAuthAccessTokenService(SystemConfiguration _sysConfig) {
        super(_sysConfig);
    }

    @Transactional
    @Override
    public OAuthAccessToken create(OAuthAccessToken oauthAccessToken) {
        requireNotDisposed();
        requireArgument(oauthAccessToken != null, "Cannot update a null access token entity");
        EntityManager em = emf.get();
        OAuthAccessToken result = em.merge(oauthAccessToken);
        em.flush();
        _logger.debug("Created OAuthAccessToken to : {}", result);
        return result;
    }

    @Override
    @Transactional
    public OAuthAccessToken findByAccessToken(String accessToken) {
        requireNotDisposed();
        requireArgument(accessToken != null, "access token can not be null");
        _logger.debug("Querying Access Token by access_token: {} ", accessToken);

        OAuthAccessToken result = OAuthAccessToken.findByAccessToken(emf.get(), accessToken);
        _logger.debug("Query for Access Token row having access_token {} resulted in : {}", accessToken, result);
        return result;
    }

    @Override
    @Transactional
    public OAuthAccessToken findLatestAccessTokensByClientIdUserId(String clientId, String userId) {
        requireNotDisposed();
        requireArgument(clientId != null, "client_id can not be null");
        requireArgument(clientId != null, "user_id can not be null");
        _logger.debug("Querying Access Token by client_id: {} and user_id: {}", clientId, userId);

        OAuthAccessToken result = OAuthAccessToken.findLatestAccessTokensByClientIdUserId(emf.get(), clientId, userId);
        _logger.debug("Querying Access Token by client_id: {} and user_id: {} resulted in : {}", clientId, userId, result);
        return result;
    }

    @Override
    @Transactional
    public int updateAccessToken(String oldToken, String newToken, Timestamp expires) {
        requireNotDisposed();
        requireArgument(oldToken != null, "old access token can not be null");
        requireArgument(newToken != null, "new access token can not be null");
        requireArgument(expires != null, "expires cannot be null or empty");
        _logger.debug("Updating Access Token and  expires for access_token: {} to: {}", oldToken, newToken, expires);
        int result = OAuthAccessToken.updateAccessToken(emf.get(), oldToken, newToken, expires);
        return result;
    }



}

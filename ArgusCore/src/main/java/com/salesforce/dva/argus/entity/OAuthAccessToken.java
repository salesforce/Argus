package com.salesforce.dva.argus.entity;

import javax.persistence.*;
import java.sql.Timestamp;
import java.util.Objects;
/**
 * The entity which encapsulates Access Token related information.
 *
 * <p>Fields that determine uniqueness are:</p>
 *
 * <ul>
 *   <li>ACCESS_TOKEN</li>
 * </ul>
 *
 * <p>Fields that cannot be null are:</p>
 *
 * <ul>
 *   <li>USER_ID</li>
 *   <li>SCOPE</li>
 * </ul>
 *
 * @author	Gaurav Kumar (gaurav.kumar@salesforce.com)
 */

@Entity
@Table(name = "oauth_access_tokens")
@NamedQueries(
    {
        @NamedQuery(name = "OAuthAccessToken.findByAccessToken",
                query = "SELECT a FROM OAuthAccessToken a WHERE a.accessToken = :token"),
        @NamedQuery(name = "OAuthAccessToken.findLatestAccessTokensByClientIdUserId",
                query = "SELECT a FROM OAuthAccessToken a WHERE a.clientId = :clientId AND a.userId = :userId order by a.expires desc"),
        @NamedQuery(
                name = "OAuthAccessToken.updateAccessToken",
                query = "UPDATE OAuthAccessToken a SET a.accessToken = :newToken, a.expires = :expires WHERE a.accessToken = :oldToken")
    }
)
public class OAuthAccessToken {
    private String accessToken;
    private String clientId;
    private String userId;
    private Timestamp expires;
    private String scope;
    private String refreshToken;
    private Timestamp refreshExpires;

    /** Creates a new OAuthAccessToken object. */
    public OAuthAccessToken() {
        // Empty Constructor
    }

    public OAuthAccessToken(String accessToken, String clientId, String userId, Timestamp expires, String scope, String refreshToken, Timestamp refreshExpires) {
        this.accessToken = accessToken;
        this.clientId = clientId;
        this.userId = userId;
        this.expires = expires;
        this.scope = scope;
        this.refreshToken = refreshToken;
        this.refreshExpires = refreshExpires;
    }

    @Id
    @Column(name = "access_token", nullable = false, length = 200)
    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    @Basic
    @Column(name = "client_id", nullable = false, length = 80)
    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    @Basic
    @Column(name = "user_id", nullable = true, length = 80)
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @Basic
    @Column(name = "expires", nullable = false)
    public Timestamp getExpires() {
        return expires;
    }

    public void setExpires(Timestamp expires) {
        this.expires = expires;
    }

    @Basic
    @Column(name = "scope", nullable = true, length = 4000)
    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    @Basic
    @Column(name = "refresh_token", nullable = false, length = 200)
    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    @Basic
    @Column(name = "refresh_expires", nullable = false)
    public Timestamp getRefreshExpires() {
        return refreshExpires;
    }

    public void setRefreshExpires(Timestamp refreshExpires) {
        this.refreshExpires = refreshExpires;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OAuthAccessToken that = (OAuthAccessToken) o;
        return Objects.equals(accessToken, that.accessToken) &&
                Objects.equals(clientId, that.clientId) &&
                Objects.equals(userId, that.userId) &&
                Objects.equals(expires, that.expires) &&
                Objects.equals(scope, that.scope) &&
                Objects.equals(refreshToken, that.refreshToken) &&
                Objects.equals(refreshExpires, that.refreshExpires);
    }

    @Override
    public int hashCode() {

        return Objects.hash(accessToken, clientId, userId, expires, scope, refreshToken, refreshExpires);
    }

    //~ Static Methods **************************************************************************************************************************************

    public static OAuthAccessToken findByAccessToken(EntityManager em, String token) {
        TypedQuery<OAuthAccessToken> query = em.createNamedQuery("OAuthAccessToken.findByAccessToken", OAuthAccessToken.class);
        try {
            query.setParameter("token", token);
            return query.getSingleResult();
        } catch (NoResultException ex) {
            return null;
        }
    }

    public static OAuthAccessToken findLatestAccessTokensByClientIdUserId(EntityManager em, String clientId, String userId) {
        TypedQuery<OAuthAccessToken> query = em.createNamedQuery("OAuthAccessToken.findLatestAccessTokensByClientIdUserId", OAuthAccessToken.class);
        try {
            query.setParameter("userId", clientId);
            query.setParameter("clientId", userId);
            return query.getSingleResult();
        } catch (NoResultException ex) {
            return null;
        }
    }

    public static int updateAccessToken(EntityManager em, String oldToken, String newToken, Timestamp expires) {
        TypedQuery<OAuthAccessToken> query = em.createNamedQuery("OAuthAccessToken.updateAccessToken", OAuthAccessToken.class);
        query.setParameter("oldToken", oldToken);
        query.setParameter("newToken", newToken);
        query.setParameter("expires", expires, TemporalType.TIMESTAMP);
        return query.executeUpdate();
    }

}

/* Copyright (c) 2018, Salesforce.com, Inc.  All rights reserved. */

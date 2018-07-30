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

package com.salesforce.dva.argus.entity;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;

/**
 * The entity which encapsulates Authorization code related information.
 *
 * <p>Fields that determine uniqueness are:</p>
 *
 * <ul>
 *   <li>AUTHORIZATION_CODE</li>
 * </ul>
 *
 * <p>Fields that cannot be null are:</p>
 *
 * <ul>
 *   <li>USER_ID</li>
 *   <li>REDIRECT_URI</li>
 *   <li>SCOPE</li>
 *   <li>STATE</li>
 * </ul>
 *
 * @author	Gaurav Kumar (gaurav.kumar@salesforce.com) , Chandravyas Annakula(cannakula@salesforce.com)
 */

@SuppressWarnings("serial")
@Entity
@Table(name = "oauth_authorization_codes")
@NamedQueries(
        {
                @NamedQuery(name = "OAuthAuthorizationCode.findByCodeAndRedirectURI",
                        query = "SELECT a FROM OAuthAuthorizationCode a WHERE a.authorizationCode = :code AND a.redirectUri = :uri"),
                @NamedQuery(name = "OAuthAuthorizationCode.findByCodeAndState",
                        query = "SELECT a FROM OAuthAuthorizationCode a WHERE a.authorizationCode = :code AND a.state = :state"),
                @NamedQuery(
                        name = "OAuthAuthorizationCode.updateExpires",
                        query = "UPDATE OAuthAuthorizationCode a SET a.expires = :expires WHERE a.authorizationCode = :code"
                ),
                @NamedQuery(
                        name = "OAuthAuthorizationCode.updateUserId",
                        query = "UPDATE OAuthAuthorizationCode a SET a.userId = :userId WHERE a.authorizationCode = :code and a.state = :state"
                ),
                @NamedQuery(
                        name = "OAuthAuthorizationCode.countByUserId",
                        query = "SELECT count(a) FROM OAuthAuthorizationCode a WHERE a.userId = :username "
                ),
                @NamedQuery(
                        name = "OAuthAuthorizationCode.deleteByExpiresAndUserId",
                        query = "DELETE FROM OAuthAuthorizationCode a WHERE a.expires < :currenttime and a.userId=:username"
                ),
                @NamedQuery(
                        name = "OAuthAuthorizationCode.deleteByUserId",
                        query = "DELETE FROM OAuthAuthorizationCode a WHERE a.userId=:username"
                )
        }
)
public class OAuthAuthorizationCode implements Serializable {
    private String authorizationCode;
    private String clientId;
    private String userId;
    private String redirectUri;
    private Timestamp expires;
    private String scope;
    private String state;

    public OAuthAuthorizationCode(String authorizationCode, String clientId, String userId, String redirectUri, Timestamp expires, String scope, String state) {
        this.authorizationCode = authorizationCode;
        this.clientId = clientId;
        this.userId = userId;
        this.redirectUri = redirectUri;
        this.expires = expires;
        this.scope = scope;
        this.state = state;
    }

    /** Creates a new Authorization Code object. */
    protected OAuthAuthorizationCode() {
        // Empty Constructor
    }

    @Id
    @Column(name = "authorization_code", nullable = false, length = 40)
    public String getAuthorizationCode() {
        return authorizationCode;
    }

    public void setAuthorizationCode(String authorizationCode) {
        this.authorizationCode = authorizationCode;
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
    @Column(name = "redirect_uri", nullable = true, length = 2000)
    public String getRedirectUri() {
        return redirectUri;
    }

    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
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
    @Column(name = "state", nullable = true, length = 1000)
    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OAuthAuthorizationCode that = (OAuthAuthorizationCode) o;
        return Objects.equals(authorizationCode, that.authorizationCode) &&
                Objects.equals(clientId, that.clientId) &&
                Objects.equals(userId, that.userId) &&
                Objects.equals(redirectUri, that.redirectUri) &&
                Objects.equals(expires, that.expires) &&
                Objects.equals(scope, that.scope) &&
                Objects.equals(state, that.state);
    }

    @Override
    public int hashCode() {

        return Objects.hash(authorizationCode, clientId, userId, redirectUri, expires, scope, state);
    }

    //~ Static Methods **************************************************************************************************************************************

    public static OAuthAuthorizationCode findByCodeAndState(EntityManager em, String code, String state) {
        TypedQuery<OAuthAuthorizationCode> query = em.createNamedQuery("OAuthAuthorizationCode.findByCodeAndState", OAuthAuthorizationCode.class);
        try {
            query.setParameter("code", code);
            query.setParameter("state", state);
            return query.getSingleResult();
        } catch (NoResultException ex) {
            return null;
        }
    }

    public static OAuthAuthorizationCode findByCodeAndRedirectURI(EntityManager em, String code, String uri) {
        TypedQuery<OAuthAuthorizationCode> query = em.createNamedQuery("OAuthAuthorizationCode.findByCodeAndRedirectURI", OAuthAuthorizationCode.class);
        try {
            query.setParameter("code", code);
            query.setParameter("uri", uri);
            return query.getSingleResult();
        } catch (NoResultException ex) {
            return null;
        }
    }

    public static int updateExpires(EntityManager em, String code, Timestamp expires) {
        TypedQuery<OAuthAuthorizationCode> query = em.createNamedQuery("OAuthAuthorizationCode.updateExpires", OAuthAuthorizationCode.class);
        query.setParameter("code", code);
        query.setParameter("expires", expires, TemporalType.TIMESTAMP);
        return query.executeUpdate();
    }

    public static int updateUserId(EntityManager em, String code, String state, String userId) {
        TypedQuery<OAuthAuthorizationCode> query = em.createNamedQuery("OAuthAuthorizationCode.updateUserId", OAuthAuthorizationCode.class);
        query.setParameter("code", code);
        query.setParameter("state", state);
        query.setParameter("userId", userId);
        return query.executeUpdate();
    }

    public static int findByUserId(EntityManager em, String userName) {
        TypedQuery<Long> query = em.createNamedQuery("OAuthAuthorizationCode.countByUserId", Long.class);
        try {
            query.setParameter("username", userName);
            return query.getSingleResult().intValue();
        } catch (NoResultException ex) {
            return 0;
        }
    }

    public static int deleteByTimeStamp(EntityManager em, Timestamp currentTime,String userName) {
        TypedQuery<OAuthAuthorizationCode> query = em.createNamedQuery("OAuthAuthorizationCode.deleteByExpiresAndUserId", OAuthAuthorizationCode.class);
            query.setParameter("currenttime", currentTime);
            query.setParameter("username", userName);
            return query.executeUpdate();
    }

    public static int deleteByUserId(EntityManager em, String userName) {
        TypedQuery<OAuthAuthorizationCode> query = em.createNamedQuery("OAuthAuthorizationCode.deleteByUserId", OAuthAuthorizationCode.class);
        query.setParameter("username", userName);
        return query.executeUpdate();
    }


}

/* Copyright (c) 2018, Salesforce.com, Inc.  All rights reserved. */
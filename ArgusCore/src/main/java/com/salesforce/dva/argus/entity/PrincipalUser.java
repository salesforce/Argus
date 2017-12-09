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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.salesforce.dva.argus.system.SystemAssert;

import java.io.IOException;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.MapKeyColumn;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.NoResultException;
import javax.persistence.OneToMany;
import javax.persistence.TypedQuery;

/**
 * The entity which encapsulates information about a user.
 *
 * <p>Fields that determine uniqueness are:</p>
 *
 * <ul>
 *   <li>USER_NAME</li>
 * </ul>
 *
 * <p>Fields that must be unique are:</p>
 *
 * <ul>
 *   <li>USER_NAME</li>
 *   <li>EMAIL</li>
 * </ul>
 *
 * <p>Fields that cannot be null are:</p>
 *
 * <ul>
 *   <li>USER_NAME</li>
 *   <li>EMAIL</li>
 * </ul>
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
@SuppressWarnings("serial")
@Entity
@NamedQueries(
    {
        @NamedQuery(name = "PrincipalUser.findByUserName", query = "SELECT p FROM PrincipalUser p WHERE p.userName = :userName"),
        @NamedQuery(
            name = "PrincipalUser.findUniqueUserCount", query = "SELECT count(p.id) FROM PrincipalUser p"
        )
    }
)
public class PrincipalUser extends JPAEntity implements Serializable {
	
	public static class Serializer extends JsonSerializer<PrincipalUser> {

	    @Override
	    public void serialize(PrincipalUser user, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonProcessingException {
	        jgen.writeStartObject();
	        jgen.writeStringField("id", user.getId().toString());
	        jgen.writeStringField("username", user.getUserName());
	        jgen.writeStringField("email", user.getEmail());
	        jgen.writeBooleanField("privileged", user.isPrivileged());
	        jgen.writeObjectField("preferences", user.getPreferences());
	        
	        jgen.writeArrayFieldStart("ownedDashboardIds");
	        for(Dashboard dashboard : user.getOwnedDashboards()) {
	        	jgen.writeNumber(dashboard.getId());
	        }
	        jgen.writeEndArray();
	        
	        jgen.writeNumberField("createdDate", user.getCreatedDate().getTime());
	        jgen.writeNumberField("modifiedDate", user.getModifiedDate().getTime());
	        jgen.writeFieldName("createdBy");
	        jgen.writeNumber(user.getCreatedBy().getId());
	        jgen.writeFieldName("modifiedBy");
	        jgen.writeNumber(user.getModifiedBy().getId());
	        jgen.writeEndObject();
	    }
	}
	
	public static class Deserializer extends JsonDeserializer<PrincipalUser> {

		@Override
		public PrincipalUser deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
			
			PrincipalUser user = new PrincipalUser();
			JsonNode rootNode = jp.getCodec().readTree(jp);
			
			BigInteger id = new BigInteger(rootNode.get("id").asText());
			user.id = id;
			
			String username = rootNode.get("username").asText();
			user.setUserName(username);
			
			String email = rootNode.get("email").asText();
			user.setEmail(email);
			
			user.setPrivileged(rootNode.get("privileged").asBoolean());
			
			Map<Preference, String> preferences = new HashMap<>();
			JsonNode preferencesNode = rootNode.get("preferences");
			if(preferencesNode.isObject()) {
				Iterator<Entry<String, JsonNode>> fieldsIter = preferencesNode.fields();
				while(fieldsIter.hasNext()) {
					Entry<String, JsonNode> field  = fieldsIter.next();
					preferences.put(Preference.valueOf(field.getKey()), field.getValue().asText());
				}
			}
			user.preferences = preferences;
			
			List<Dashboard> ownedDashboards = new ArrayList<>();
			JsonNode ownedDashboardIds = rootNode.get("ownedDashboardIds");
			if(ownedDashboardIds.isArray()) {
				for(JsonNode ownedDashboardId : ownedDashboardIds) {
					Dashboard d = new Dashboard();
					d.id = new BigInteger(ownedDashboardId.asText());
					ownedDashboards.add(d);
				}
			}
			user.setOwnedDashboards(ownedDashboards);
			
			user.setCreatedBy(new PrincipalUser(new BigInteger(rootNode.get("createdBy").asText())));
			user.createdDate = new Date(rootNode.get("createdDate").asLong());
			
			user.setModifiedBy(new PrincipalUser(new BigInteger(rootNode.get("modifiedBy").asText())));
			user.modifiedDate = new Date(rootNode.get("modifiedDate").asLong());
			
			return user;
		}
		
	}

    //~ Instance fields ******************************************************************************************************************************

    @Basic(optional = false)
    @Column(nullable = false, unique = true)
    private String userName;
    
    @Basic(optional = false)
    @Column(nullable = false, unique = true)
    private String email;
    
    @ElementCollection
    @MapKeyColumn(name = "name")
    @Column(name = "preference")
    private Map<Preference, String> preferences = new HashMap<>();
    
    @OneToMany(mappedBy = "owner")
    private List<Dashboard> ownedDashboards = new ArrayList<>();
    
    private boolean privileged = false;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new PrincipalUser object.
     *
     * @param  userName  The user name for the
     * @param  email     The email address for the user.
     */
    private PrincipalUser(String userName, String email) {
        this(null, userName, email);
    }

    /**
     * Creates a new PrincipalUser object.
     *
     * @param  creator   The user that created this principal.
     * @param  userName  The unique user name.
     * @param  email     An email address for the user.
     */
    public PrincipalUser(PrincipalUser creator, String userName, String email) {
        super(creator);
        setUserName(userName);
        setEmail(email);
    }

    /** Creates a new PrincipalUser object. */
    protected PrincipalUser() {
        super(null);
    }
    
    private PrincipalUser(BigInteger id) {
    	this.id = id;
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the unique user count.
     *
     * @param   em  The entity manager to use.
     *
     * @return  The unique user count.
     */
    public static long findUniqueUserCount(EntityManager em) {
        TypedQuery<Long> query = em.createNamedQuery("PrincipalUser.findUniqueUserCount", Long.class);

        return query.getSingleResult();
    }

    /**
     * Finds the application database user account for the provided user name.
     *
     * @param   em        The entity manager to use.
     * @param   userName  The user name for which to retrieve the account information for.
     *
     * @return  The user account or null if no account exists.
     */
    public static PrincipalUser findByUserName(EntityManager em, String userName) {
        Class<PrincipalUser> type = PrincipalUser.class;
        TypedQuery<PrincipalUser> query = em.createNamedQuery("PrincipalUser.findByUserName", type);

        try {
            return query.setParameter("userName", userName).getSingleResult();
        } catch (NoResultException ex) {
            return null;
        }
    }

    /* Method provided to be called using reflection to discretely create the admin user if needed. */
    private static PrincipalUser createAdminUser() {
        PrincipalUser result = new PrincipalUser("admin", "argus-admin@salesforce.com");

        result.id = BigInteger.ONE;
        result.setPrivileged(true);
        return result;
    }
    
    /* Method provided to be called using reflection to discretely create the admin user if needed. */
    private static PrincipalUser createDefaultUser() {
        PrincipalUser result = new PrincipalUser("default", "default@default.com");

        result.id = BigInteger.valueOf(2);
        result.setPrivileged(false);
        return result;
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the user name for the user.
     *
     * @return  The user name.
     */
    public String getUserName() {
        return userName;
    }

    /**
     * Updates the email address for the user.
     *
     * @param  email  The new email address.
     */
    public void setEmail(String email) {
        SystemAssert.requireArgument(email != null && !email.isEmpty(), "Email cannot be null or empty.");
        this.email = email;
    }

    /**
     * Updates the user name for the user.
     *
     * @param  userName  The new user name.
     */
    public void setUserName(String userName) {
        SystemAssert.requireArgument(userName != null && !userName.isEmpty(), "Username cannot be null or empty.");
        this.userName = userName;
    }

    /**
     * Returns the email address for the user.
     *
     * @return  The email address for the user.
     */
    public String getEmail() {
        return email;
    }

    /**
     * Updates the preferences for the user.
     *
     * @param  preferences  The preferences for the user.
     */
    public void setPreferences(Map<Preference, String> preferences) {
        this.preferences = preferences;
    }

    /**
     * Updates the preferences for the user.
     *
     * @return  The new preferences for the user.
     */
    public Map<Preference, String> getPreferences() {
        return preferences;
    }

    /**
     * Returns the list of owned dashboards.
     *
     * @return  The list of owned dashboards.
     */
    public List<Dashboard> getOwnedDashboards() {
        return ownedDashboards;
    }

    /**
     * Sets the list of owned dashboards.
     *
     * @param  ownedDashboards  The list of owned dashboards.
     */
    public void setOwnedDashboards(List<Dashboard> ownedDashboards) {
        this.ownedDashboards = ownedDashboards;
    }

    /**
     * Indicates whether the user has been granted administrator privileges.
     *
     * @return  True if the user has administrator privileges.
     */
    public boolean isPrivileged() {
        return privileged;
    }

    /**
     * Grants or revokes administrator privileges.
     *
     * @param  privileged  True to grant or false to revoke.
     */
    protected void setPrivileged(boolean privileged) {
        this.privileged = privileged;
    }

    @Override
    public int hashCode() {
        int hash = 7;

        hash = 97 * hash + (this.userName != null ? this.userName.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        final PrincipalUser other = (PrincipalUser) obj;

        if ((this.userName == null) ? (other.userName != null) : !this.userName.equals(other.userName)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "PrincipalUser{" + "userName=" + userName + ", email=" + email + ", preferences=" + preferences + ", privileged=" + privileged + '}';
    }

    //~ Enums ****************************************************************************************************************************************

    /**
     * Enumerates the valid preference fields.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    public enum Preference {

        DISPLAY_NAME;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

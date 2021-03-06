/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.faces.context.FacesContext;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.StringUtils;
import org.gluu.site.ldap.persistence.LdapEntryManager;
import org.gluu.site.ldap.persistence.exception.EntryPersistenceException;
import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.AutoCreate;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Logger;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.contexts.Context;
import org.jboss.seam.contexts.Contexts;
import org.jboss.seam.log.Log;
import org.jboss.seam.security.Identity;
import org.xdi.ldap.model.CustomEntry;
import org.xdi.ldap.model.GluuStatus;
import org.xdi.model.SimpleProperty;
import org.xdi.model.ldap.GluuLdapConfiguration;
import org.xdi.oxauth.authorize.ws.rs.AuthorizeAction;
import org.xdi.oxauth.model.authorize.AuthorizeRequestParam;
import org.xdi.oxauth.model.common.CustomAttribute;
import org.xdi.oxauth.model.common.Prompt;
import org.xdi.oxauth.model.common.SessionId;
import org.xdi.oxauth.model.common.SimpleUser;
import org.xdi.oxauth.model.common.User;
import org.xdi.oxauth.model.session.OAuthCredentials;
import org.xdi.oxauth.model.util.Util;
import org.xdi.oxauth.util.ServerUtil;
import org.xdi.util.StringHelper;

/**
 * Authentication service methods
 *
 * @author Yuriy Movchan Date: 02.04.2013
 */
@Scope(ScopeType.STATELESS)
@Name("authenticationService")
@AutoCreate
public class AuthenticationService {

    private static final String STORED_REQUEST_PARAMETERS = "stored_request_parameters";
    private static final String STORED_ORIGIN_HEADERS = "stored_origin_headers";

	@Logger
    private Log log;

    @In
    private Identity identity;

    @In
    private OAuthCredentials credentials;

    @In(required = false, value = AppInitializer.LDAP_AUTH_CONFIG_NAME)
    // created by app initializer
    private List<GluuLdapConfiguration> ldapAuthConfigs;

    @In
    private LdapEntryManager ldapEntryManager;

    @In(required = true, value = AppInitializer.LDAP_AUTH_ENTRY_MANAGER_NAME)
    private List<LdapEntryManager> ldapAuthEntryManagers;

    @In
    private UserService userService;

    @In
    private SessionIdService sessionIdService;

    /**
     * Authenticate user.
     *
     * @param userName The username.
     * @param password The user's password.
     * @return <code>true</code> if success, otherwise <code>false</code>.
     */
    public boolean authenticate(String userName, String password) {
        log.debug("Authenticating user with LDAP: username: {0}", userName);
        if (this.ldapAuthConfigs == null) {
            return localAuthenticate(userName, password);
        } else {
            return externalAuthenticate(userName, password);
        }
    }

	private boolean localAuthenticate(String userName, String password) {
		User user = userService.getUser(userName);
		if (user != null) {
		    if (!checkUserStatus(user)) {
		    	return false;
		    }

		    // Use local LDAP server for user authentication
		    boolean authenticated = ldapEntryManager.authenticate(user.getDn(), password);
		    if (authenticated) {
		        credentials.setUser(user);
		    	updateLastLogonUserTime(user);
		    }

		    return authenticated;
		}

		return false;
	}

	private boolean externalAuthenticate(String keyValue, String password) {
		for (int i = 0; i < this.ldapAuthConfigs.size(); i++) {
			GluuLdapConfiguration ldapAuthConfig = this.ldapAuthConfigs.get(i);
			LdapEntryManager ldapAuthEntryManager = this.ldapAuthEntryManagers.get(i);

			String primaryKey = "uid";
            if (StringHelper.isNotEmpty(ldapAuthConfig.getPrimaryKey())) {
                primaryKey = ldapAuthConfig.getPrimaryKey();
            }

            String localPrimaryKey = "uid";
            if (StringHelper.isNotEmpty(ldapAuthConfig.getLocalPrimaryKey())) {
                localPrimaryKey = ldapAuthConfig.getLocalPrimaryKey();
            }

            boolean authenticated = authenticate(ldapAuthConfig, ldapAuthEntryManager, keyValue, password, primaryKey, localPrimaryKey);
			if (authenticated) {
				return authenticated;
			}
		}

		return false;
    }

    public boolean authenticate(String keyValue, String password, String primaryKey, String localPrimaryKey) {
    	if (this.ldapAuthConfigs == null) {
    		return authenticate(null, ldapEntryManager, keyValue, password, primaryKey, localPrimaryKey);
    	}
    	
		for (int i = 0; i < this.ldapAuthConfigs.size(); i++) {
			GluuLdapConfiguration ldapAuthConfig = this.ldapAuthConfigs.get(i);
			LdapEntryManager ldapAuthEntryManager = this.ldapAuthEntryManagers.get(i);

			boolean authenticated = authenticate(ldapAuthConfig, ldapAuthEntryManager, keyValue, password, primaryKey, localPrimaryKey);
			if (authenticated) {
				return authenticated;
			}
		}
		
		return false;
    }

    public boolean authenticate(GluuLdapConfiguration ldapAuthConfig, LdapEntryManager ldapAuthEntryManager, String keyValue, String password, String primaryKey, String localPrimaryKey) {
		log.debug("Attempting to find userDN by primary key: '{0}' and key value: '{1}'", primaryKey, keyValue);

		List<?> baseDNs;
		if (ldapAuthConfig == null) {
			baseDNs = Arrays.asList(userService.getDnForUser(null));
		} else {
			baseDNs = ldapAuthConfig.getBaseDNs();
		}

		if (baseDNs != null && !baseDNs.isEmpty()) {
		    for (Object baseDnProperty : baseDNs) {
		        String baseDn;
		        if (baseDnProperty instanceof SimpleProperty) {
		        	baseDn = ((SimpleProperty) baseDnProperty).getValue();
		        } else {
		        	baseDn = baseDnProperty.toString();
		        }

		        User user = getUserByAttribute(ldapAuthEntryManager, baseDn, primaryKey, keyValue);
		        if (user != null) {
		            String userDn = user.getDn();
		            log.debug("Attempting to authenticate userDN: {0}", userDn);
		            if (ldapAuthEntryManager.authenticate(userDn, password)) {
		                log.debug("User authenticated: {0}", userDn);

		                log.debug("Attempting to find userDN by local primary key: {0}", localPrimaryKey);
		                User localUser = userService.getUserByAttribute(localPrimaryKey, keyValue);
		                if (localUser != null) {
		                    if (!checkUserStatus(localUser)) {
		                    	return false;
		                    }

		                    credentials.setUser(localUser);
		                    updateLastLogonUserTime(localUser);

		                    return true;
		                }
		            }
		        }
		    }
		} else {
		    log.error("There are no baseDns specified in authentication configuration.");
		}

		return false;
	}

    public boolean authenticate(String userName) {
        log.debug("Authenticating user with LDAP: username: {0}", userName);
        User user = userService.getUser(userName);
        if (user != null) {
            if (!checkUserStatus(user)) {
            	return false;
            }

            credentials.setUsername(user.getUserId());
            credentials.setUser(user);
            updateLastLogonUserTime(user);

            return true;
        }
        
        return false;
    }

    private User getUserByAttribute(LdapEntryManager ldapAuthEntryManager, String baseDn, String attributeName, String attributeValue) {
        log.debug("Getting user information from LDAP: attributeName = '{0}', attributeValue = '{1}'", attributeName, attributeValue);

        SimpleUser sampleUser = new SimpleUser();
        sampleUser.setDn(baseDn);

        List<CustomAttribute> customAttributes = new ArrayList<CustomAttribute>();
        customAttributes.add(new CustomAttribute(attributeName, attributeValue));

        sampleUser.setCustomAttributes(customAttributes);

        List<User> entries = ldapAuthEntryManager.findEntries(sampleUser, 1);
        log.debug("Found '{0}' entries", entries.size());

        if (entries.size() > 0) {
			SimpleUser foundUser = entries.get(0);
			
			return ldapAuthEntryManager.find(User.class, foundUser.getDn());
        } else {
            return null;
        }
    }

	private boolean checkUserStatus(User user) {
		CustomAttribute userStatus = userService.getCustomAttribute(user, "gluuStatus");

		if ((userStatus == null) || GluuStatus.INACTIVE.equals(GluuStatus.getByValue(userStatus.getValue()))) {
		    log.warn("User '{0}' was disabled", user.getUserId());
		    return false;
		}
		
		return true;
	}

	private void updateLastLogonUserTime(User user) {
		CustomEntry customEntry = new CustomEntry();
		customEntry.setDn(user.getDn());

		org.xdi.ldap.model.CustomAttribute customAttribute = new org.xdi.ldap.model.CustomAttribute("oxLastLogonTime", new Date());
		customEntry.getCustomAttributes().add(customAttribute);

		try {
			ldapEntryManager.merge(customEntry);
		} catch (EntryPersistenceException epe) {
		    log.error("Failed to update oxLastLoginTime of user '{0}'", user.getUserId());
		}
	}

    public String parametersAsString() throws UnsupportedEncodingException {
        final Map<String, String> parameterMap = getParametersMap(null);

        return parametersAsString(parameterMap);
    }

    public String parametersAsString(final Map<String, String> parameterMap) throws UnsupportedEncodingException {
        final StringBuilder sb = new StringBuilder();
        final Set<Entry<String, String>> set = parameterMap.entrySet();
        for (Map.Entry<String, String> entry : set) {
            final String value = (String) entry.getValue();
            if (StringUtils.isNotBlank(value)) {
                sb.append(entry.getKey()).append("=").append(URLEncoder.encode(value, Util.UTF8_STRING_ENCODING)).append("&");
            }
        }

        String result = sb.toString();
        if (result.endsWith("&")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    public Map<String, String> getParametersMap(List<String> extraParameters) {
        final Map<String, String> parameterMap = new HashMap<String, String>(FacesContext.getCurrentInstance().getExternalContext()
                .getRequestParameterMap());

        return getParametersMap(extraParameters, parameterMap);
    }

    public Map<String, String> getParametersMap(List<String> extraParameters, final Map<String, String> parameterMap) {
		final List<String> allowedParameters = new ArrayList<String>(AuthorizeAction.ALLOWED_PARAMETER);
        allowedParameters.addAll(Arrays.asList("auth_mode", "auth_level", "auth_step"));

        putInMap(parameterMap, "auth_mode");
        putInMap(parameterMap, "auth_level");
        putInMap(parameterMap, "auth_step");

        if (extraParameters != null) {
            for (String extraParameter : extraParameters) {
                putInMap(parameterMap, extraParameter);
            }

            allowedParameters.addAll(extraParameters);
        }

        for (Iterator<Entry<String, String>> it = parameterMap.entrySet().iterator(); it.hasNext(); ) {
            Entry<String, String> entry = it.next();
            if (!allowedParameters.contains(entry.getKey())) {
                it.remove();
            }
        }

        return parameterMap;
	}

    private static void putInMap(Map<String, String> map, String p_name) {
        if (map == null) {
            return;
        }

        final Object o = Contexts.getEventContext().get(p_name);
        if (o instanceof String) {
            final String s = (String) o;
            map.put(p_name, s);
        } else if (o instanceof Boolean) {
            final Boolean b = (Boolean) o;
            map.put(p_name, b.toString());
        }
    }

    public void configureEventUser(boolean interactive) {
        User user = credentials.getUser();
        if (user != null) {
            configureEventUser(user, interactive);
        }
    }

    public void configureEventUser(User user, boolean interactive) {
        final List<Prompt> prompts = new ArrayList<Prompt>();
        if (!interactive) {
            prompts.add(Prompt.NONE);
        }

        SessionId sessionId = sessionIdService.generateSessionId(user.getDn(), prompts);
        sessionId.setAuthenticationTime(new Date());

        configureEventUser(sessionId, prompts);
    }

    public void configureEventUser(SessionId sessionId, List<Prompt> prompts) {
        identity.addRole("user");

        sessionIdService.updateSessionWithLastUsedDate(sessionId, prompts);

        Contexts.getEventContext().set("sessionUser", sessionId);
    }

    public void storeRequestParametersInSession() {
        Contexts.getSessionContext().set(STORED_REQUEST_PARAMETERS, getParametersMap(null));
    }

    @SuppressWarnings("unchecked")
	public Map<String, String> restoreRequestParametersFromSession() {
    	Context eventContext = Contexts.getEventContext();
    	Context sessionContext = Contexts.getSessionContext();
    	if (sessionContext.isSet(STORED_REQUEST_PARAMETERS)) {
    		Map<String, String> storedRequestParameters = (Map<String, String>) sessionContext.get(STORED_REQUEST_PARAMETERS);
    		sessionContext.remove(STORED_REQUEST_PARAMETERS);
    		eventContext.set(STORED_REQUEST_PARAMETERS, storedRequestParameters);

    		return storedRequestParameters;
    	}
    	
    	return null;
    }

	public void storeRequestHeadersInSession(HttpServletRequest request) {
		String originHeaders = request.getParameter(AuthorizeRequestParam.ORIGIN_HEADERS);
		if (StringHelper.isEmpty(originHeaders)) {
			return;
		}

        log.debug("Storing origin_headers: '{0}'", originHeaders);
		Context sessionContext = Contexts.getSessionContext();
		sessionContext.set(STORED_ORIGIN_HEADERS, originHeaders);
		
//		SessionIdAttribute sessionIdAttribute = new SessionIdAttribute();
//		sessionIdAttribute.setName(STORED_ORIGIN_HEADERS);
//		sessionIdAttribute.setValue(originHeaders);
//		sessionIdService.addSessionAttribute(sessionIdService.getSessionIdFromCookies(request), sessionIdAttribute);
	}

	public String getRequestHeadersFromSession() {
		Context sessionContext = Contexts.getSessionContext();
		
		if (sessionContext.isSet(STORED_ORIGIN_HEADERS)) {
			return (String) sessionContext.get(STORED_ORIGIN_HEADERS);
		}
		
		return null;
	}

    public static AuthenticationService instance() {
        return ServerUtil.instance(AuthenticationService.class);
    }
}
/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.model.uma;

import java.util.Date;
import java.util.List;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.annotate.JsonPropertyOrder;
import org.jboss.resteasy.annotations.providers.jaxb.IgnoreMediaTypes;

/**
 * Resource set that needs protection by registering a resource set description
 * at the AM.
 *
 * @author Yuriy Movchan
 * @author Yuriy Zabrovarnyy
 *         Date: 10/03/2012
 */

// try to ignore jettison as it's recommended here: http://docs.jboss.org/resteasy/docs/2.3.4.Final/userguide/html/json.html
@IgnoreMediaTypes("application/*+json")
@JsonPropertyOrder({"resource_set_id", "scopes", "expires_at"})
//@JsonIgnoreProperties(ignoreUnknown = true)
//@JsonRootName(value = "resourceSetPermissionRequest")
@XmlRootElement
public class ResourceSetPermissionRequest {

    private String resourceSetId;
    private List<String> scopes;
    private Date expiresAt;

    public ResourceSetPermissionRequest() {
    }

    public ResourceSetPermissionRequest(String p_resourceSetId, List<String> p_scopes) {
        resourceSetId = p_resourceSetId;
        scopes = p_scopes;
    }

    @JsonProperty(value = "resource_set_id")
    @XmlElement(name = "resource_set_id")
    public String getResourceSetId() {
        return resourceSetId;
    }

    public void setResourceSetId(String resourceSetId) {
        this.resourceSetId = resourceSetId;
    }

    @JsonProperty(value = "scopes")
    @XmlElement(name = "scopes")
    public List<String> getScopes() {
        return scopes;
    }

    public void setScopes(List<String> scopes) {
        this.scopes = scopes;
    }

    @JsonProperty(value = "expires_at")
    @XmlElement(name = "expires_at")
    public Date getExpiresAt() {
        return expiresAt != null ? new Date(expiresAt.getTime()) : null;
    }

    public void setExpiresAt(Date p_expiresAt) {
        expiresAt = p_expiresAt != null ? new Date(p_expiresAt.getTime()) : null;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("ResourceSetPermissionRequest");
        sb.append("{resourceSetId='").append(resourceSetId).append('\'');
        sb.append(", scopes=").append(scopes);
        sb.append(", expiresAt=").append(expiresAt);
        sb.append('}');
        return sb.toString();
    }
}

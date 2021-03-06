/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.service.uma;

import java.util.Date;

import org.xdi.oxauth.model.uma.ResourceSetPermissionRequest;
import org.xdi.oxauth.model.uma.persistence.ResourceSetPermission;

/**
 * @author Yuriy Zabrovarnyy
 * @version 0.9, 11/02/2013
 */

public interface IResourceSetPermissionManager {

    public void addResourceSetPermission(ResourceSetPermission resourceSetPermission, String clientDn);

    public ResourceSetPermission getResourceSetPermissionByTicket(String resourceSetPermissionTicket);

    public String getResourceSetPermissionTicketByConfigurationCode(String configurationCode, String clientDn);

    public ResourceSetPermission createResourceSetPermission(String amHost, String host, ResourceSetPermissionRequest resourceSetPermissionRequest, Date expirationDate);

    public void deleteResourceSetPermission(String resourceSetPermissionTicket);

    public void cleanupResourceSetPermissions(Date now);
}

/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.uma.ws.rs;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Logger;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.log.Log;
import org.xdi.oxauth.model.common.uma.UmaRPT;
import org.xdi.oxauth.model.error.ErrorResponseFactory;
import org.xdi.oxauth.model.uma.ResourceSetPermissionRequest;
import org.xdi.oxauth.model.uma.RptStatusRequest;
import org.xdi.oxauth.model.uma.RptStatusResponse;
import org.xdi.oxauth.model.uma.UmaErrorResponseType;
import org.xdi.oxauth.model.uma.persistence.ResourceSetPermission;
import org.xdi.oxauth.service.uma.RPTManager;
import org.xdi.oxauth.service.uma.ScopeService;
import org.xdi.oxauth.service.uma.UmaValidationService;
import org.xdi.oxauth.util.ServerUtil;

/**
 * The endpoint at which the host requests the status of an RPT presented to it by a requester.
 * The endpoint is RPT introspection profile implementation defined by
 * http://docs.kantarainitiative.org/uma/draft-uma-core.html#uma-bearer-token-profile
 *
 * @author Yuriy Movchan Date: 10/23/2012
 * @author Yuriy Zabrovarnyy
 */
@Name("rptStatusRestWebService")
public class RptStatusRestWebServiceImpl implements RptStatusRestWebService {

    @Logger
    private Log log;
    //    @In
//    private TokenService tokenService;
    @In
    private ErrorResponseFactory errorResponseFactory;
    //    @In
//    private AuthorizationGrantList authorizationGrantList;
    @In
    private RPTManager rptManager;
    @In
    private UmaValidationService umaValidationService;
    @In
    private ScopeService umaScopeService;

    public Response requestRptStatus(String authorization, RptStatusRequest tokenStatusRequest) {
        try {
            umaValidationService.validateAuthorizationWithProtectScope(authorization);

//            final String authorizationToken = tokenService.getTokenFromAuthorizationParameter(authorization);
//            final AuthorizationGrant authorizationGrant = authorizationGrantList.getAuthorizationGrantByAccessToken(authorizationToken);
//            final AbstractToken accessToken = authorizationGrant.getAccessToken(authorizationToken);
            final UmaRPT rpt = rptManager.getRPTByCode(tokenStatusRequest.getRpt());

            if (!isValid(rpt)) {
                return Response.status(Response.Status.OK).entity(new RptStatusResponse(false)).cacheControl(ServerUtil.cacheControl(true)).build();
            }

            final List<ResourceSetPermissionRequest> permissions = buildStatusResponsePermissions(rpt);

            // active status
            final RptStatusResponse statusResponse = new RptStatusResponse();
            statusResponse.setActive(true);
            statusResponse.setExpiresAt(rpt.getExpirationDate());
            statusResponse.setIssuedAt(rpt.getCreationDate());
            statusResponse.setPermissions(permissions);

            // convert manually to avoid possible conflict between resteasy providers, e.g. jettison, jackson
            final String entity = ServerUtil.asJson(statusResponse);

            return Response.status(Response.Status.OK).entity(entity).cacheControl(ServerUtil.cacheControl(true)).build();
        } catch (Exception ex) {
            log.error("Exception happened", ex);
            if (ex instanceof WebApplicationException) {
                throw (WebApplicationException) ex;
            }

            throw new WebApplicationException(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(errorResponseFactory.getUmaJsonErrorResponse(UmaErrorResponseType.SERVER_ERROR)).build());
        }
    }

    private boolean isValid(UmaRPT p_rpt) {
        if (p_rpt != null) {
            p_rpt.checkExpired();
            return p_rpt.isValid();
        }
        return false;
    }

    private boolean isValid(ResourceSetPermission resourceSetPermission) {
        if (resourceSetPermission != null) {
            resourceSetPermission.checkExpired();
            return resourceSetPermission.isValid();
        }
        return false;
    }

    private List<ResourceSetPermissionRequest> buildStatusResponsePermissions(UmaRPT p_rpt) {
        final List<ResourceSetPermissionRequest> result = new ArrayList<ResourceSetPermissionRequest>();
        if (p_rpt != null) {
            final List<ResourceSetPermission> rptPermissions = rptManager.getRptPermissions(p_rpt);
            if (rptPermissions != null && !rptPermissions.isEmpty()) {
                for (ResourceSetPermission permission : rptPermissions) {
                    if (isValid(permission)) {
                        final ResourceSetPermissionRequest toAdd = ServerUtil.convert(permission, umaScopeService);
                        if (toAdd != null) {
                            result.add(toAdd);
                        }
                    } else {
                        log.debug("Ignore permission, skip it in response because permission is not valid. Permission dn: {0}, rpt dn: {1}",
                                permission.getDn(), p_rpt.getDn());
                    }
                }
            }
        }
        return result;
    }
}

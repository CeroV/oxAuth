/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.ws.rs;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.testng.annotations.Parameters;
import org.testng.annotations.Test;
import org.xdi.oxauth.BaseTest;
import org.xdi.oxauth.client.AuthorizationRequest;
import org.xdi.oxauth.client.AuthorizationResponse;
import org.xdi.oxauth.client.AuthorizeClient;
import org.xdi.oxauth.client.RegisterClient;
import org.xdi.oxauth.client.RegisterRequest;
import org.xdi.oxauth.client.RegisterResponse;
import org.xdi.oxauth.client.TokenClient;
import org.xdi.oxauth.client.TokenResponse;
import org.xdi.oxauth.client.UserInfoClient;
import org.xdi.oxauth.client.UserInfoRequest;
import org.xdi.oxauth.client.UserInfoResponse;
import org.xdi.oxauth.client.model.authorize.Claim;
import org.xdi.oxauth.client.model.authorize.ClaimValue;
import org.xdi.oxauth.client.model.authorize.JwtAuthorizationRequest;
import org.xdi.oxauth.model.common.AuthorizationMethod;
import org.xdi.oxauth.model.common.Prompt;
import org.xdi.oxauth.model.common.ResponseType;
import org.xdi.oxauth.model.crypto.encryption.BlockEncryptionAlgorithm;
import org.xdi.oxauth.model.crypto.encryption.KeyEncryptionAlgorithm;
import org.xdi.oxauth.model.crypto.signature.RSAPrivateKey;
import org.xdi.oxauth.model.crypto.signature.SignatureAlgorithm;
import org.xdi.oxauth.model.jwt.JwtClaimName;
import org.xdi.oxauth.model.register.ApplicationType;
import org.xdi.oxauth.model.util.StringUtils;

/**
 * Functional tests for User Info Web Services (HTTP)
 *
 * @author Javier Rojas Blum Date: 11.30.2011
 */
public class UserInfoRestWebServiceHttpTest extends BaseTest {

    @Parameters({"userId", "userSecret", "clientId", "redirectUri"})
    @Test
    public void requestUserInfoImplicitFlow(final String userId, final String userSecret,
                                            final String clientId, final String redirectUri) throws Exception {
        showTitle("requestUserInfoImplicitFlow");

        // 1. Request authorization
        List<ResponseType> responseTypes = new ArrayList<ResponseType>();
        responseTypes.add(ResponseType.TOKEN);
        responseTypes.add(ResponseType.ID_TOKEN);
        List<String> scopes = new ArrayList<String>();
        scopes.add("openid");
        scopes.add("profile");
        scopes.add("address");
        scopes.add("email");
        String nonce = UUID.randomUUID().toString();
        String state = "af0ifjsldkj";

        AuthorizationRequest request = new AuthorizationRequest(responseTypes, clientId, scopes, redirectUri, nonce);
        request.setState(state);
        request.setAuthUsername(userId);
        request.setAuthPassword(userSecret);
        request.getPrompts().add(Prompt.NONE);

        AuthorizeClient authorizeClient = new AuthorizeClient(authorizationEndpoint);
        authorizeClient.setRequest(request);
        AuthorizationResponse response1 = authorizeClient.exec();

        showClient(authorizeClient);
        assertEquals(response1.getStatus(), 302, "Unexpected response code: " + response1.getStatus());
        assertNotNull(response1.getLocation(), "The location is null");
        assertNotNull(response1.getAccessToken(), "The access token is null");
        assertNotNull(response1.getState(), "The state is null");
        assertNotNull(response1.getTokenType(), "The token type is null");
        assertNotNull(response1.getExpiresIn(), "The expires in value is null");
        assertNotNull(response1.getScope(), "The scope must be null");
        assertNotNull(response1.getIdToken(), "The id token must be null");

        String accessToken = response1.getAccessToken();

        // 2. Request user info
        UserInfoClient userInfoClient = new UserInfoClient(userInfoEndpoint);
        UserInfoResponse response2 = userInfoClient.execUserInfo(accessToken);

        showClient(userInfoClient);
        assertEquals(response2.getStatus(), 200, "Unexpected response code: " + response2.getStatus());
        assertNotNull(response2.getClaim(JwtClaimName.SUBJECT_IDENTIFIER));
        assertNotNull(response2.getClaim(JwtClaimName.NAME));
        assertNotNull(response2.getClaim(JwtClaimName.GIVEN_NAME));
        assertNotNull(response2.getClaim(JwtClaimName.FAMILY_NAME));
        assertNotNull(response2.getClaim(JwtClaimName.EMAIL));
        assertNotNull(response2.getClaim(JwtClaimName.ZONEINFO));
        assertNotNull(response2.getClaim(JwtClaimName.LOCALE));
    }

    @Parameters({"userId", "userSecret", "clientId", "clientSecret"})
    @Test
    public void requestUserInfoPasswordFlow(final String userId, final String userSecret,
                                            final String clientId, final String clientSecret) throws Exception {
        showTitle("requestUserInfoPasswordFlow");

        // 1. Request authorization
        String username = userId;
        String password = userSecret;
        String scope = "openid profile address email";

        TokenClient tokenClient = new TokenClient(tokenEndpoint);
        TokenResponse response1 = tokenClient.execResourceOwnerPasswordCredentialsGrant(username, password, scope,
                clientId, clientSecret);

        showClient(tokenClient);
        assertEquals(response1.getStatus(), 200, "Unexpected response code: " + response1.getStatus());
        assertNotNull(response1.getEntity(), "The entity is null");
        assertNotNull(response1.getAccessToken(), "The access token is null");
        assertNotNull(response1.getTokenType(), "The token type is null");
        assertNotNull(response1.getRefreshToken(), "The refresh token is null");
        assertNotNull(response1.getScope(), "The scope is null");

        String accessToken = response1.getAccessToken();

        // 2. Request user info
        UserInfoClient userInfoClient = new UserInfoClient(userInfoEndpoint);
        UserInfoResponse response2 = userInfoClient.execUserInfo(accessToken);

        showClient(userInfoClient);
        assertEquals(response2.getStatus(), 200, "Unexpected response code: " + response2.getStatus());
        assertNotNull(response2.getClaim(JwtClaimName.SUBJECT_IDENTIFIER));
        assertNotNull(response2.getClaim(JwtClaimName.NAME));
        assertNotNull(response2.getClaim(JwtClaimName.GIVEN_NAME));
        assertNotNull(response2.getClaim(JwtClaimName.FAMILY_NAME));
        assertNotNull(response2.getClaim(JwtClaimName.EMAIL));
        assertNotNull(response2.getClaim(JwtClaimName.ZONEINFO));
        assertNotNull(response2.getClaim(JwtClaimName.LOCALE));
    }

    @Test
    public void requestUserInfoInvalidRequest() throws Exception {
        showTitle("requestUserInfoInvalidRequest");

        UserInfoClient userInfoClient = new UserInfoClient(userInfoEndpoint);
        UserInfoResponse response = userInfoClient.execUserInfo(null);

        showClient(userInfoClient);
        assertEquals(response.getStatus(), 400, "Unexpected response code: " + response.getStatus());
        assertNotNull(response.getErrorType(), "Unexpected result: errorType not found");
        assertNotNull(response.getErrorDescription(), "Unexpected result: errorDescription not found");
    }

    @Test
    public void requestUserInfoInvalidToken() throws Exception {
        showTitle("requestUserInfoInvalidToken");

        UserInfoClient userInfoClient = new UserInfoClient(userInfoEndpoint);
        UserInfoResponse response = userInfoClient.execUserInfo("INVALID_ACCESS_TOKEN");

        showClient(userInfoClient);
        assertEquals(response.getStatus(), 400, "Unexpected response code: " + response.getStatus());
        assertNotNull(response.getErrorType(), "Unexpected result: errorType not found");
        assertNotNull(response.getErrorDescription(), "Unexpected result: errorDescription not found");
    }

    @Parameters({"userId", "userSecret", "clientId", "redirectUri"})
    @Test
    public void requestUserInfoInsufficientScope(final String userId, final String userSecret,
                                                 final String clientId, final String redirectUri) throws Exception {
        showTitle("requestUserInfoInsufficientScope");

        // 1. Request authorization
        List<ResponseType> responseTypes = new ArrayList<ResponseType>();
        responseTypes.add(ResponseType.TOKEN);
        responseTypes.add(ResponseType.ID_TOKEN);
        List<String> scopes = new ArrayList<String>();
        scopes.add("picture");
        String nonce = UUID.randomUUID().toString();
        String state = "af0ifjsldkj";

        AuthorizationRequest request = new AuthorizationRequest(responseTypes, clientId, scopes, redirectUri, nonce);
        request.setState(state);
        request.getPrompts().add(Prompt.NONE);
        request.setAuthUsername(userId);
        request.setAuthPassword(userSecret);

        AuthorizeClient authorizeClient = new AuthorizeClient(authorizationEndpoint);
        authorizeClient.setRequest(request);
        AuthorizationResponse authorizationResponse = authorizeClient.exec();

        showClient(authorizeClient);
        assertEquals(authorizationResponse.getStatus(), 302, "Unexpected response code: " + authorizationResponse.getStatus());
        assertNotNull(authorizationResponse.getLocation(), "The location is null");
        assertNotNull(authorizationResponse.getAccessToken(), "The access token is null");
        assertNotNull(authorizationResponse.getState(), "The state is null");
        assertNotNull(authorizationResponse.getTokenType(), "The token type is null");
        assertNotNull(authorizationResponse.getExpiresIn(), "The expires in value is null");
        assertNotNull(authorizationResponse.getScope(), "The scope must be null");
        assertNotNull(authorizationResponse.getIdToken(), "The id token must be null");

        String accessToken = authorizationResponse.getAccessToken();

        // 2. Request user info
        UserInfoClient userInfoClient = new UserInfoClient(userInfoEndpoint);
        UserInfoResponse userInfoResponse = userInfoClient.execUserInfo(accessToken);

        showClient(userInfoClient);
        assertEquals(userInfoResponse.getStatus(), 403, "Unexpected response code: " + userInfoResponse.getStatus());
        assertNotNull(userInfoResponse.getErrorType(), "Unexpected result: errorType not found");
        assertNotNull(userInfoResponse.getErrorDescription(), "Unexpected result: errorDescription not found");
    }

    @Parameters({"userId", "userSecret", "clientId", "clientSecret", "redirectUri"})
    @Test
    public void requestUserInfoAdditionalClaims(final String userId, final String userSecret,
                                                final String clientId, final String clientSecret,
                                                final String redirectUri) throws Exception {
        showTitle("requestUserInfoAdditionalClaims");

        // 1. Request authorization
        List<ResponseType> responseTypes = new ArrayList<ResponseType>();
        responseTypes.add(ResponseType.TOKEN);
        List<String> scopes = new ArrayList<String>();
        scopes.add("openid");
        scopes.add("profile");
        scopes.add("address");
        scopes.add("email");
        String nonce = UUID.randomUUID().toString();
        String state = "af0ifjsldkj";

        AuthorizationRequest authorizationRequest = new AuthorizationRequest(
                responseTypes, clientId, scopes, redirectUri, nonce);
        authorizationRequest.setState(state);
        authorizationRequest.getPrompts().add(Prompt.NONE);
        authorizationRequest.setAuthUsername(userId);
        authorizationRequest.setAuthPassword(userSecret);

        JwtAuthorizationRequest jwtAuthorizationRequest = new JwtAuthorizationRequest(authorizationRequest, SignatureAlgorithm.HS256, clientSecret);
        jwtAuthorizationRequest.addUserInfoClaim(new Claim("invalid", ClaimValue.createEssential(false)));
        jwtAuthorizationRequest.addUserInfoClaim(new Claim("iname", ClaimValue.createNull()));
        jwtAuthorizationRequest.addUserInfoClaim(new Claim("gluuStatus", ClaimValue.createEssential(true)));
        jwtAuthorizationRequest.addUserInfoClaim(new Claim("gluuWhitePagesListed", ClaimValue.createEssential(true)));
        jwtAuthorizationRequest.addUserInfoClaim(new Claim("o", ClaimValue.createEssential(true)));
        String authJwt = jwtAuthorizationRequest.getEncodedJwt();
        authorizationRequest.setRequest(authJwt);

        AuthorizeClient authorizeClient = new AuthorizeClient(authorizationEndpoint);
        authorizeClient.setRequest(authorizationRequest);
        AuthorizationResponse response1 = authorizeClient.exec();

        showClient(authorizeClient);
        assertEquals(response1.getStatus(), 302, "Unexpected response code: " + response1.getStatus());
        assertNotNull(response1.getLocation(), "The location is null");
        assertNotNull(response1.getAccessToken(), "The access token is null");
        assertNotNull(response1.getState(), "The state is null");
        assertNotNull(response1.getTokenType(), "The token type is null");
        assertNotNull(response1.getExpiresIn(), "The expires in value is null");
        assertNotNull(response1.getScope(), "The scope must be null");

        String accessToken = response1.getAccessToken();

        // 2. Request user info (AUTHORIZATION_REQUEST_HEADER_FIELD)
        UserInfoRequest userInfoRequest1 = new UserInfoRequest(accessToken);
        userInfoRequest1.setAuthorizationMethod(AuthorizationMethod.AUTHORIZATION_REQUEST_HEADER_FIELD);
        UserInfoClient userInfoClient1 = new UserInfoClient(userInfoEndpoint);
        userInfoClient1.setRequest(userInfoRequest1);
        UserInfoResponse response2 = userInfoClient1.exec();

        showClient(userInfoClient1);
        assertEquals(response2.getStatus(), 200, "Unexpected response code: " + response2.getStatus());
        assertNotNull(response2.getClaim(JwtClaimName.SUBJECT_IDENTIFIER));
        assertNotNull(response2.getClaim(JwtClaimName.NAME));
        assertNotNull(response2.getClaim(JwtClaimName.GIVEN_NAME));
        assertNotNull(response2.getClaim(JwtClaimName.FAMILY_NAME));
        assertNotNull(response2.getClaim(JwtClaimName.EMAIL));
        assertNotNull(response2.getClaim(JwtClaimName.ZONEINFO));
        assertNotNull(response2.getClaim(JwtClaimName.LOCALE));

        // Custom Claims
        assertNotNull(response2.getClaim("iname"), "Unexpected result: iname not found");
        assertNotNull(response2.getClaim("gluuStatus"), "Unexpected result: gluuStatus not found");
        assertNotNull(response2.getClaim("gluuWhitePagesListed"), "Unexpected result: gluuWhitePagesListed not found");
        assertNotNull(response2.getClaim("o"), "Unexpected result: organization not found");

        // 3. Request user info (FORM_ENCODED_BODY_PARAMETER)
        UserInfoRequest userInfoRequest2 = new UserInfoRequest(accessToken);
        userInfoRequest2.setAuthorizationMethod(AuthorizationMethod.FORM_ENCODED_BODY_PARAMETER);
        UserInfoClient userInfoClient2 = new UserInfoClient(userInfoEndpoint);
        userInfoClient2.setRequest(userInfoRequest2);
        UserInfoResponse response3 = userInfoClient2.exec();

        showClient(userInfoClient2);
        assertEquals(response3.getStatus(), 200, "Unexpected response code: " + response3.getStatus());
        assertNotNull(response3.getClaim(JwtClaimName.SUBJECT_IDENTIFIER));
        assertNotNull(response3.getClaim(JwtClaimName.NAME));
        assertNotNull(response3.getClaim(JwtClaimName.GIVEN_NAME));
        assertNotNull(response3.getClaim(JwtClaimName.FAMILY_NAME));
        assertNotNull(response3.getClaim(JwtClaimName.EMAIL));
        assertNotNull(response3.getClaim(JwtClaimName.ZONEINFO));
        assertNotNull(response3.getClaim(JwtClaimName.LOCALE));

        // 4. Request user info (URL_QUERY_PARAMETER)
        UserInfoRequest userInfoRequest3 = new UserInfoRequest(accessToken);
        userInfoRequest3.setAuthorizationMethod(AuthorizationMethod.URL_QUERY_PARAMETER);
        UserInfoClient userInfoClient3 = new UserInfoClient(userInfoEndpoint);
        userInfoClient3.setRequest(userInfoRequest3);
        UserInfoResponse response4 = userInfoClient3.exec();

        showClient(userInfoClient3);
        assertEquals(response4.getStatus(), 200, "Unexpected response code: " + response4.getStatus());
        assertNotNull(response4.getClaim(JwtClaimName.SUBJECT_IDENTIFIER));
        assertNotNull(response4.getClaim(JwtClaimName.NAME));
        assertNotNull(response4.getClaim(JwtClaimName.GIVEN_NAME));
        assertNotNull(response4.getClaim(JwtClaimName.FAMILY_NAME));
        assertNotNull(response4.getClaim(JwtClaimName.EMAIL));
        assertNotNull(response4.getClaim(JwtClaimName.ZONEINFO));
        assertNotNull(response4.getClaim(JwtClaimName.LOCALE));
    }

    @Parameters({"redirectUris", "redirectUri", "userId", "userSecret"})
    @Test
    public void requestUserInfoHS256(final String redirectUris, final String redirectUri,
                                     final String userId, final String userSecret) throws Exception {
        showTitle("requestUserInfoHS256");

        List<ResponseType> responseTypes = Arrays.asList(
                ResponseType.TOKEN,
                ResponseType.ID_TOKEN);

        // 1. Dynamic Registration
        RegisterRequest registerRequest = new RegisterRequest(ApplicationType.WEB, "oxAuth test app",
                StringUtils.spaceSeparatedToList(redirectUris));
        registerRequest.setResponseTypes(responseTypes);
        registerRequest.setUserInfoSignedResponseAlg(SignatureAlgorithm.HS256);
        registerRequest.addCustomAttribute("oxAuthTrustedClient", "true");

        RegisterClient registerClient = new RegisterClient(registrationEndpoint);
        registerClient.setRequest(registerRequest);
        RegisterResponse registerResponse = registerClient.exec();

        showClient(registerClient);
        assertEquals(registerResponse.getStatus(), 200, "Unexpected response code: " + registerResponse.getEntity());
        assertNotNull(registerResponse.getClientId());
        assertNotNull(registerResponse.getClientSecret());
        assertNotNull(registerResponse.getRegistrationAccessToken());
        assertNotNull(registerResponse.getClientSecretExpiresAt());

        String clientId = registerResponse.getClientId();
        String clientSecret = registerResponse.getClientSecret();

        // 2. Request authorization
        List<String> scopes = Arrays.asList(
                "openid",
                "profile",
                "address",
                "email");
        String nonce = UUID.randomUUID().toString();
        String state = "af0ifjsldkj";

        AuthorizationRequest authorizationRequest = new AuthorizationRequest(responseTypes, clientId, scopes, redirectUri, nonce);
        authorizationRequest.setState(state);
        authorizationRequest.setAuthUsername(userId);
        authorizationRequest.setAuthPassword(userSecret);
        authorizationRequest.getPrompts().add(Prompt.NONE);

        AuthorizeClient authorizeClient = new AuthorizeClient(authorizationEndpoint);
        authorizeClient.setRequest(authorizationRequest);
        AuthorizationResponse authorizationResponse = authorizeClient.exec();

        showClient(authorizeClient);
        assertEquals(authorizationResponse.getStatus(), 302, "Unexpected response code: " + authorizationResponse.getStatus());
        assertNotNull(authorizationResponse.getLocation(), "The location is null");
        assertNotNull(authorizationResponse.getAccessToken(), "The access token is null");
        assertNotNull(authorizationResponse.getState(), "The state is null");
        assertNotNull(authorizationResponse.getTokenType(), "The token type is null");
        assertNotNull(authorizationResponse.getExpiresIn(), "The expires in value is null");
        assertNotNull(authorizationResponse.getScope(), "The scope must be null");
        assertNotNull(authorizationResponse.getIdToken(), "The id token must be null");

        String accessToken = authorizationResponse.getAccessToken();

        // 3. Request user info
        UserInfoClient userInfoClient = new UserInfoClient(userInfoEndpoint);
        userInfoClient.setSharedKey(clientSecret);
        UserInfoResponse userInfoResponse = userInfoClient.execUserInfo(accessToken);

        showClient(userInfoClient);
        assertEquals(userInfoResponse.getStatus(), 200, "Unexpected response code: " + userInfoResponse.getStatus());
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.SUBJECT_IDENTIFIER));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.GIVEN_NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.FAMILY_NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.PICTURE));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.EMAIL));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.ZONEINFO));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.LOCALE));
    }

    @Parameters({"redirectUris", "redirectUri", "userId", "userSecret"})
    @Test
    public void requestUserInfoHS384(final String redirectUris, final String redirectUri,
                                     final String userId, final String userSecret) throws Exception {
        showTitle("requestUserInfoHS384");

        List<ResponseType> responseTypes = Arrays.asList(
                ResponseType.TOKEN,
                ResponseType.ID_TOKEN);

        // 1. Dynamic Registration
        RegisterRequest registerRequest = new RegisterRequest(ApplicationType.WEB, "oxAuth test app",
                StringUtils.spaceSeparatedToList(redirectUris));
        registerRequest.setResponseTypes(responseTypes);
        registerRequest.setUserInfoSignedResponseAlg(SignatureAlgorithm.HS384);
        registerRequest.addCustomAttribute("oxAuthTrustedClient", "true");

        RegisterClient registerClient = new RegisterClient(registrationEndpoint);
        registerClient.setRequest(registerRequest);
        RegisterResponse registerResponse = registerClient.exec();

        showClient(registerClient);
        assertEquals(registerResponse.getStatus(), 200, "Unexpected response code: " + registerResponse.getEntity());
        assertNotNull(registerResponse.getClientId());
        assertNotNull(registerResponse.getClientSecret());
        assertNotNull(registerResponse.getRegistrationAccessToken());
        assertNotNull(registerResponse.getClientSecretExpiresAt());

        String clientId = registerResponse.getClientId();
        String clientSecret = registerResponse.getClientSecret();

        // 2. Request authorization
        List<String> scopes = Arrays.asList(
                "openid",
                "profile",
                "address",
                "email");
        String nonce = UUID.randomUUID().toString();
        String state = "af0ifjsldkj";

        AuthorizationRequest authorizationRequest = new AuthorizationRequest(responseTypes, clientId, scopes, redirectUri, nonce);
        authorizationRequest.setState(state);
        authorizationRequest.setAuthUsername(userId);
        authorizationRequest.setAuthPassword(userSecret);
        authorizationRequest.getPrompts().add(Prompt.NONE);

        AuthorizeClient authorizeClient = new AuthorizeClient(authorizationEndpoint);
        authorizeClient.setRequest(authorizationRequest);
        AuthorizationResponse authorizationResponse = authorizeClient.exec();

        showClient(authorizeClient);
        assertEquals(authorizationResponse.getStatus(), 302, "Unexpected response code: " + authorizationResponse.getStatus());
        assertNotNull(authorizationResponse.getLocation(), "The location is null");
        assertNotNull(authorizationResponse.getAccessToken(), "The access token is null");
        assertNotNull(authorizationResponse.getState(), "The state is null");
        assertNotNull(authorizationResponse.getTokenType(), "The token type is null");
        assertNotNull(authorizationResponse.getExpiresIn(), "The expires in value is null");
        assertNotNull(authorizationResponse.getScope(), "The scope must be null");
        assertNotNull(authorizationResponse.getIdToken(), "The id token must be null");

        String accessToken = authorizationResponse.getAccessToken();

        // 3. Request user info
        UserInfoClient userInfoClient = new UserInfoClient(userInfoEndpoint);
        userInfoClient.setSharedKey(clientSecret);
        UserInfoResponse userInfoResponse = userInfoClient.execUserInfo(accessToken);

        showClient(userInfoClient);
        assertEquals(userInfoResponse.getStatus(), 200, "Unexpected response code: " + userInfoResponse.getStatus());
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.SUBJECT_IDENTIFIER));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.GIVEN_NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.FAMILY_NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.PICTURE));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.EMAIL));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.ZONEINFO));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.LOCALE));
    }

    @Parameters({"redirectUris", "redirectUri", "userId", "userSecret"})
    @Test
    public void requestUserInfoHS512(final String redirectUris, final String redirectUri,
                                     final String userId, final String userSecret) throws Exception {
        showTitle("requestUserInfoHS512");

        List<ResponseType> responseTypes = Arrays.asList(
                ResponseType.TOKEN,
                ResponseType.ID_TOKEN);

        // 1. Dynamic Registration
        RegisterRequest registerRequest = new RegisterRequest(ApplicationType.WEB, "oxAuth test app",
                StringUtils.spaceSeparatedToList(redirectUris));
        registerRequest.setResponseTypes(responseTypes);
        registerRequest.setUserInfoSignedResponseAlg(SignatureAlgorithm.HS512);
        registerRequest.addCustomAttribute("oxAuthTrustedClient", "true");

        RegisterClient registerClient = new RegisterClient(registrationEndpoint);
        registerClient.setRequest(registerRequest);
        RegisterResponse registerResponse = registerClient.exec();

        showClient(registerClient);
        assertEquals(registerResponse.getStatus(), 200, "Unexpected response code: " + registerResponse.getEntity());
        assertNotNull(registerResponse.getClientId());
        assertNotNull(registerResponse.getClientSecret());
        assertNotNull(registerResponse.getRegistrationAccessToken());
        assertNotNull(registerResponse.getClientSecretExpiresAt());

        String clientId = registerResponse.getClientId();
        String clientSecret = registerResponse.getClientSecret();

        // 2. Request authorization
        List<String> scopes = Arrays.asList(
                "openid",
                "profile",
                "address",
                "email");
        String nonce = UUID.randomUUID().toString();
        String state = "af0ifjsldkj";

        AuthorizationRequest authorizationRequest = new AuthorizationRequest(responseTypes, clientId, scopes, redirectUri, nonce);
        authorizationRequest.setState(state);
        authorizationRequest.setAuthUsername(userId);
        authorizationRequest.setAuthPassword(userSecret);
        authorizationRequest.getPrompts().add(Prompt.NONE);

        AuthorizeClient authorizeClient = new AuthorizeClient(authorizationEndpoint);
        authorizeClient.setRequest(authorizationRequest);
        AuthorizationResponse authorizationResponse = authorizeClient.exec();

        showClient(authorizeClient);
        assertEquals(authorizationResponse.getStatus(), 302, "Unexpected response code: " + authorizationResponse.getStatus());
        assertNotNull(authorizationResponse.getLocation(), "The location is null");
        assertNotNull(authorizationResponse.getAccessToken(), "The access token is null");
        assertNotNull(authorizationResponse.getState(), "The state is null");
        assertNotNull(authorizationResponse.getTokenType(), "The token type is null");
        assertNotNull(authorizationResponse.getExpiresIn(), "The expires in value is null");
        assertNotNull(authorizationResponse.getScope(), "The scope must be null");
        assertNotNull(authorizationResponse.getIdToken(), "The id token must be null");

        String accessToken = authorizationResponse.getAccessToken();

        // 3. Request user info
        UserInfoClient userInfoClient = new UserInfoClient(userInfoEndpoint);
        userInfoClient.setSharedKey(clientSecret);
        UserInfoResponse userInfoResponse = userInfoClient.execUserInfo(accessToken);

        showClient(userInfoClient);
        assertEquals(userInfoResponse.getStatus(), 200, "Unexpected response code: " + userInfoResponse.getStatus());
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.SUBJECT_IDENTIFIER));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.GIVEN_NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.FAMILY_NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.PICTURE));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.EMAIL));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.ZONEINFO));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.LOCALE));
    }

    @Parameters({"redirectUris", "redirectUri", "userId", "userSecret"})
    @Test
    public void requestUserInfoRS256(final String redirectUris, final String redirectUri,
                                     final String userId, final String userSecret) throws Exception {
        showTitle("requestUserInfoRS256");

        List<ResponseType> responseTypes = Arrays.asList(
                ResponseType.TOKEN,
                ResponseType.ID_TOKEN);

        // 1. Dynamic Registration
        RegisterRequest registerRequest = new RegisterRequest(ApplicationType.WEB, "oxAuth test app",
                StringUtils.spaceSeparatedToList(redirectUris));
        registerRequest.setResponseTypes(responseTypes);
        registerRequest.setUserInfoSignedResponseAlg(SignatureAlgorithm.RS256);
        registerRequest.addCustomAttribute("oxAuthTrustedClient", "true");

        RegisterClient registerClient = new RegisterClient(registrationEndpoint);
        registerClient.setRequest(registerRequest);
        RegisterResponse registerResponse = registerClient.exec();

        showClient(registerClient);
        assertEquals(registerResponse.getStatus(), 200, "Unexpected response code: " + registerResponse.getEntity());
        assertNotNull(registerResponse.getClientId());
        assertNotNull(registerResponse.getClientSecret());
        assertNotNull(registerResponse.getRegistrationAccessToken());
        assertNotNull(registerResponse.getClientSecretExpiresAt());

        String clientId = registerResponse.getClientId();

        // 2. Request authorization
        List<String> scopes = Arrays.asList(
                "openid",
                "profile",
                "address",
                "email");
        String nonce = UUID.randomUUID().toString();
        String state = "af0ifjsldkj";

        AuthorizationRequest authorizationRequest = new AuthorizationRequest(responseTypes, clientId, scopes, redirectUri, nonce);
        authorizationRequest.setState(state);
        authorizationRequest.setAuthUsername(userId);
        authorizationRequest.setAuthPassword(userSecret);
        authorizationRequest.getPrompts().add(Prompt.NONE);

        AuthorizeClient authorizeClient = new AuthorizeClient(authorizationEndpoint);
        authorizeClient.setRequest(authorizationRequest);
        AuthorizationResponse authorizationResponse = authorizeClient.exec();

        showClient(authorizeClient);
        assertEquals(authorizationResponse.getStatus(), 302, "Unexpected response code: " + authorizationResponse.getStatus());
        assertNotNull(authorizationResponse.getLocation(), "The location is null");
        assertNotNull(authorizationResponse.getAccessToken(), "The access token is null");
        assertNotNull(authorizationResponse.getState(), "The state is null");
        assertNotNull(authorizationResponse.getTokenType(), "The token type is null");
        assertNotNull(authorizationResponse.getExpiresIn(), "The expires in value is null");
        assertNotNull(authorizationResponse.getScope(), "The scope must be null");
        assertNotNull(authorizationResponse.getIdToken(), "The id token must be null");

        String accessToken = authorizationResponse.getAccessToken();

        // 3. Request user info
        UserInfoClient userInfoClient = new UserInfoClient(userInfoEndpoint);
        userInfoClient.setJwksUri(jwksUri);
        UserInfoResponse userInfoResponse = userInfoClient.execUserInfo(accessToken);

        showClient(userInfoClient);
        assertEquals(userInfoResponse.getStatus(), 200, "Unexpected response code: " + userInfoResponse.getStatus());
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.SUBJECT_IDENTIFIER));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.GIVEN_NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.FAMILY_NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.PICTURE));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.EMAIL));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.ZONEINFO));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.LOCALE));
    }

    @Parameters({"redirectUris", "redirectUri", "userId", "userSecret"})
    @Test
    public void requestUserInfoRS384(final String redirectUris, final String redirectUri,
                                     final String userId, final String userSecret) throws Exception {
        showTitle("requestUserInfoRS384");

        List<ResponseType> responseTypes = Arrays.asList(
                ResponseType.TOKEN,
                ResponseType.ID_TOKEN);

        // 1. Dynamic Registration
        RegisterRequest registerRequest = new RegisterRequest(ApplicationType.WEB, "oxAuth test app",
                StringUtils.spaceSeparatedToList(redirectUris));
        registerRequest.setResponseTypes(responseTypes);
        registerRequest.setUserInfoSignedResponseAlg(SignatureAlgorithm.RS384);
        registerRequest.addCustomAttribute("oxAuthTrustedClient", "true");

        RegisterClient registerClient = new RegisterClient(registrationEndpoint);
        registerClient.setRequest(registerRequest);
        RegisterResponse registerResponse = registerClient.exec();

        showClient(registerClient);
        assertEquals(registerResponse.getStatus(), 200, "Unexpected response code: " + registerResponse.getEntity());
        assertNotNull(registerResponse.getClientId());
        assertNotNull(registerResponse.getClientSecret());
        assertNotNull(registerResponse.getRegistrationAccessToken());
        assertNotNull(registerResponse.getClientSecretExpiresAt());

        String clientId = registerResponse.getClientId();

        // 2. Request authorization
        List<String> scopes = Arrays.asList(
                "openid",
                "profile",
                "address",
                "email");
        String nonce = UUID.randomUUID().toString();
        String state = "af0ifjsldkj";

        AuthorizationRequest authorizationRequest = new AuthorizationRequest(responseTypes, clientId, scopes, redirectUri, nonce);
        authorizationRequest.setState(state);
        authorizationRequest.setAuthUsername(userId);
        authorizationRequest.setAuthPassword(userSecret);
        authorizationRequest.getPrompts().add(Prompt.NONE);

        AuthorizeClient authorizeClient = new AuthorizeClient(authorizationEndpoint);
        authorizeClient.setRequest(authorizationRequest);
        AuthorizationResponse authorizationResponse = authorizeClient.exec();

        showClient(authorizeClient);
        assertEquals(authorizationResponse.getStatus(), 302, "Unexpected response code: " + authorizationResponse.getStatus());
        assertNotNull(authorizationResponse.getLocation(), "The location is null");
        assertNotNull(authorizationResponse.getAccessToken(), "The access token is null");
        assertNotNull(authorizationResponse.getState(), "The state is null");
        assertNotNull(authorizationResponse.getTokenType(), "The token type is null");
        assertNotNull(authorizationResponse.getExpiresIn(), "The expires in value is null");
        assertNotNull(authorizationResponse.getScope(), "The scope must be null");
        assertNotNull(authorizationResponse.getIdToken(), "The id token must be null");

        String accessToken = authorizationResponse.getAccessToken();

        // 3. Request user info
        UserInfoClient userInfoClient = new UserInfoClient(userInfoEndpoint);
        userInfoClient.setJwksUri(jwksUri);
        UserInfoResponse userInfoResponse = userInfoClient.execUserInfo(accessToken);

        showClient(userInfoClient);
        assertEquals(userInfoResponse.getStatus(), 200, "Unexpected response code: " + userInfoResponse.getStatus());
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.SUBJECT_IDENTIFIER));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.GIVEN_NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.FAMILY_NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.PICTURE));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.EMAIL));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.ZONEINFO));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.LOCALE));
    }

    @Parameters({"redirectUris", "redirectUri", "userId", "userSecret"})
    @Test
    public void requestUserInfoRS512(final String redirectUris, final String redirectUri,
                                     final String userId, final String userSecret) throws Exception {
        showTitle("requestUserInfoRS512");

        List<ResponseType> responseTypes = Arrays.asList(
                ResponseType.TOKEN,
                ResponseType.ID_TOKEN);

        // 1. Dynamic Registration
        RegisterRequest registerRequest = new RegisterRequest(ApplicationType.WEB, "oxAuth test app",
                StringUtils.spaceSeparatedToList(redirectUris));
        registerRequest.setResponseTypes(responseTypes);
        registerRequest.setUserInfoSignedResponseAlg(SignatureAlgorithm.RS512);
        registerRequest.addCustomAttribute("oxAuthTrustedClient", "true");

        RegisterClient registerClient = new RegisterClient(registrationEndpoint);
        registerClient.setRequest(registerRequest);
        RegisterResponse registerResponse = registerClient.exec();

        showClient(registerClient);
        assertEquals(registerResponse.getStatus(), 200, "Unexpected response code: " + registerResponse.getEntity());
        assertNotNull(registerResponse.getClientId());
        assertNotNull(registerResponse.getClientSecret());
        assertNotNull(registerResponse.getRegistrationAccessToken());
        assertNotNull(registerResponse.getClientSecretExpiresAt());

        String clientId = registerResponse.getClientId();

        // 2. Request authorization
        List<String> scopes = Arrays.asList(
                "openid",
                "profile",
                "address",
                "email");
        String nonce = UUID.randomUUID().toString();
        String state = "af0ifjsldkj";

        AuthorizationRequest authorizationRequest = new AuthorizationRequest(responseTypes, clientId, scopes, redirectUri, nonce);
        authorizationRequest.setState(state);
        authorizationRequest.setAuthUsername(userId);
        authorizationRequest.setAuthPassword(userSecret);
        authorizationRequest.getPrompts().add(Prompt.NONE);

        AuthorizeClient authorizeClient = new AuthorizeClient(authorizationEndpoint);
        authorizeClient.setRequest(authorizationRequest);
        AuthorizationResponse authorizationResponse = authorizeClient.exec();

        showClient(authorizeClient);
        assertEquals(authorizationResponse.getStatus(), 302, "Unexpected response code: " + authorizationResponse.getStatus());
        assertNotNull(authorizationResponse.getLocation(), "The location is null");
        assertNotNull(authorizationResponse.getAccessToken(), "The access token is null");
        assertNotNull(authorizationResponse.getState(), "The state is null");
        assertNotNull(authorizationResponse.getTokenType(), "The token type is null");
        assertNotNull(authorizationResponse.getExpiresIn(), "The expires in value is null");
        assertNotNull(authorizationResponse.getScope(), "The scope must be null");
        assertNotNull(authorizationResponse.getIdToken(), "The id token must be null");

        String accessToken = authorizationResponse.getAccessToken();

        // 3. Request user info
        UserInfoClient userInfoClient = new UserInfoClient(userInfoEndpoint);
        userInfoClient.setJwksUri(jwksUri);
        UserInfoResponse userInfoResponse = userInfoClient.execUserInfo(accessToken);

        showClient(userInfoClient);
        assertEquals(userInfoResponse.getStatus(), 200, "Unexpected response code: " + userInfoResponse.getStatus());
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.SUBJECT_IDENTIFIER));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.GIVEN_NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.FAMILY_NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.PICTURE));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.EMAIL));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.ZONEINFO));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.LOCALE));
    }

    @Parameters({"redirectUris", "redirectUri", "userId", "userSecret"})
    @Test
    public void requestUserInfoES256(final String redirectUris, final String redirectUri,
                                     final String userId, final String userSecret) throws Exception {
        showTitle("requestUserInfoES256");

        List<ResponseType> responseTypes = Arrays.asList(
                ResponseType.TOKEN,
                ResponseType.ID_TOKEN);

        // 1. Dynamic Registration
        RegisterRequest registerRequest = new RegisterRequest(ApplicationType.WEB, "oxAuth test app",
                StringUtils.spaceSeparatedToList(redirectUris));
        registerRequest.setResponseTypes(responseTypes);
        registerRequest.setUserInfoSignedResponseAlg(SignatureAlgorithm.ES256);
        registerRequest.addCustomAttribute("oxAuthTrustedClient", "true");

        RegisterClient registerClient = new RegisterClient(registrationEndpoint);
        registerClient.setRequest(registerRequest);
        RegisterResponse registerResponse = registerClient.exec();

        showClient(registerClient);
        assertEquals(registerResponse.getStatus(), 200, "Unexpected response code: " + registerResponse.getEntity());
        assertNotNull(registerResponse.getClientId());
        assertNotNull(registerResponse.getClientSecret());
        assertNotNull(registerResponse.getRegistrationAccessToken());
        assertNotNull(registerResponse.getClientSecretExpiresAt());

        String clientId = registerResponse.getClientId();

        // 2. Request authorization
        List<String> scopes = Arrays.asList(
                "openid",
                "profile",
                "address",
                "email");
        String nonce = UUID.randomUUID().toString();
        String state = "af0ifjsldkj";

        AuthorizationRequest authorizationRequest = new AuthorizationRequest(responseTypes, clientId, scopes, redirectUri, nonce);
        authorizationRequest.setState(state);
        authorizationRequest.setAuthUsername(userId);
        authorizationRequest.setAuthPassword(userSecret);
        authorizationRequest.getPrompts().add(Prompt.NONE);

        AuthorizeClient authorizeClient = new AuthorizeClient(authorizationEndpoint);
        authorizeClient.setRequest(authorizationRequest);
        AuthorizationResponse authorizationResponse = authorizeClient.exec();

        showClient(authorizeClient);
        assertEquals(authorizationResponse.getStatus(), 302, "Unexpected response code: " + authorizationResponse.getStatus());
        assertNotNull(authorizationResponse.getLocation(), "The location is null");
        assertNotNull(authorizationResponse.getAccessToken(), "The access token is null");
        assertNotNull(authorizationResponse.getState(), "The state is null");
        assertNotNull(authorizationResponse.getTokenType(), "The token type is null");
        assertNotNull(authorizationResponse.getExpiresIn(), "The expires in value is null");
        assertNotNull(authorizationResponse.getScope(), "The scope must be null");
        assertNotNull(authorizationResponse.getIdToken(), "The id token must be null");

        String accessToken = authorizationResponse.getAccessToken();

        // 3. Request user info
        UserInfoClient userInfoClient = new UserInfoClient(userInfoEndpoint);
        userInfoClient.setJwksUri(jwksUri);
        UserInfoResponse userInfoResponse = userInfoClient.execUserInfo(accessToken);

        showClient(userInfoClient);
        assertEquals(userInfoResponse.getStatus(), 200, "Unexpected response code: " + userInfoResponse.getStatus());
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.SUBJECT_IDENTIFIER));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.GIVEN_NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.FAMILY_NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.PICTURE));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.EMAIL));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.ZONEINFO));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.LOCALE));
    }

    @Parameters({"redirectUris", "redirectUri", "userId", "userSecret"})
    @Test
    public void requestUserInfoES384(final String redirectUris, final String redirectUri,
                                     final String userId, final String userSecret) throws Exception {
        showTitle("requestUserInfoES384");

        List<ResponseType> responseTypes = Arrays.asList(
                ResponseType.TOKEN,
                ResponseType.ID_TOKEN);

        // 1. Dynamic Registration
        RegisterRequest registerRequest = new RegisterRequest(ApplicationType.WEB, "oxAuth test app",
                StringUtils.spaceSeparatedToList(redirectUris));
        registerRequest.setResponseTypes(responseTypes);
        registerRequest.setUserInfoSignedResponseAlg(SignatureAlgorithm.ES384);
        registerRequest.addCustomAttribute("oxAuthTrustedClient", "true");

        RegisterClient registerClient = new RegisterClient(registrationEndpoint);
        registerClient.setRequest(registerRequest);
        RegisterResponse registerResponse = registerClient.exec();

        showClient(registerClient);
        assertEquals(registerResponse.getStatus(), 200, "Unexpected response code: " + registerResponse.getEntity());
        assertNotNull(registerResponse.getClientId());
        assertNotNull(registerResponse.getClientSecret());
        assertNotNull(registerResponse.getRegistrationAccessToken());
        assertNotNull(registerResponse.getClientSecretExpiresAt());

        String clientId = registerResponse.getClientId();

        // 2. Request authorization
        List<String> scopes = Arrays.asList(
                "openid",
                "profile",
                "address",
                "email");
        String nonce = UUID.randomUUID().toString();
        String state = "af0ifjsldkj";

        AuthorizationRequest authorizationRequest = new AuthorizationRequest(responseTypes, clientId, scopes, redirectUri, nonce);
        authorizationRequest.setState(state);
        authorizationRequest.setAuthUsername(userId);
        authorizationRequest.setAuthPassword(userSecret);
        authorizationRequest.getPrompts().add(Prompt.NONE);

        AuthorizeClient authorizeClient = new AuthorizeClient(authorizationEndpoint);
        authorizeClient.setRequest(authorizationRequest);
        AuthorizationResponse authorizationResponse = authorizeClient.exec();

        showClient(authorizeClient);
        assertEquals(authorizationResponse.getStatus(), 302, "Unexpected response code: " + authorizationResponse.getStatus());
        assertNotNull(authorizationResponse.getLocation(), "The location is null");
        assertNotNull(authorizationResponse.getAccessToken(), "The access token is null");
        assertNotNull(authorizationResponse.getState(), "The state is null");
        assertNotNull(authorizationResponse.getTokenType(), "The token type is null");
        assertNotNull(authorizationResponse.getExpiresIn(), "The expires in value is null");
        assertNotNull(authorizationResponse.getScope(), "The scope must be null");
        assertNotNull(authorizationResponse.getIdToken(), "The id token must be null");

        String accessToken = authorizationResponse.getAccessToken();

        // 3. Request user info
        UserInfoClient userInfoClient = new UserInfoClient(userInfoEndpoint);
        userInfoClient.setJwksUri(jwksUri);
        UserInfoResponse userInfoResponse = userInfoClient.execUserInfo(accessToken);

        showClient(userInfoClient);
        assertEquals(userInfoResponse.getStatus(), 200, "Unexpected response code: " + userInfoResponse.getStatus());
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.SUBJECT_IDENTIFIER));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.GIVEN_NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.FAMILY_NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.PICTURE));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.EMAIL));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.ZONEINFO));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.LOCALE));
    }

    @Parameters({"redirectUris", "redirectUri", "userId", "userSecret"})
    @Test
    public void requestUserInfoES512(final String redirectUris, final String redirectUri,
                                     final String userId, final String userSecret) throws Exception {
        showTitle("requestUserInfoES512");

        List<ResponseType> responseTypes = Arrays.asList(
                ResponseType.TOKEN,
                ResponseType.ID_TOKEN);

        // 1. Dynamic Registration
        RegisterRequest registerRequest = new RegisterRequest(ApplicationType.WEB, "oxAuth test app",
                StringUtils.spaceSeparatedToList(redirectUris));
        registerRequest.setResponseTypes(responseTypes);
        registerRequest.setUserInfoSignedResponseAlg(SignatureAlgorithm.ES512);
        registerRequest.addCustomAttribute("oxAuthTrustedClient", "true");

        RegisterClient registerClient = new RegisterClient(registrationEndpoint);
        registerClient.setRequest(registerRequest);
        RegisterResponse registerResponse = registerClient.exec();

        showClient(registerClient);
        assertEquals(registerResponse.getStatus(), 200, "Unexpected response code: " + registerResponse.getEntity());
        assertNotNull(registerResponse.getClientId());
        assertNotNull(registerResponse.getClientSecret());
        assertNotNull(registerResponse.getRegistrationAccessToken());
        assertNotNull(registerResponse.getClientSecretExpiresAt());

        String clientId = registerResponse.getClientId();

        // 2. Request authorization
        List<String> scopes = Arrays.asList(
                "openid",
                "profile",
                "address",
                "email");
        String nonce = UUID.randomUUID().toString();
        String state = "af0ifjsldkj";

        AuthorizationRequest authorizationRequest = new AuthorizationRequest(responseTypes, clientId, scopes, redirectUri, nonce);
        authorizationRequest.setState(state);
        authorizationRequest.setAuthUsername(userId);
        authorizationRequest.setAuthPassword(userSecret);
        authorizationRequest.getPrompts().add(Prompt.NONE);

        AuthorizeClient authorizeClient = new AuthorizeClient(authorizationEndpoint);
        authorizeClient.setRequest(authorizationRequest);
        AuthorizationResponse authorizationResponse = authorizeClient.exec();

        showClient(authorizeClient);
        assertEquals(authorizationResponse.getStatus(), 302, "Unexpected response code: " + authorizationResponse.getStatus());
        assertNotNull(authorizationResponse.getLocation(), "The location is null");
        assertNotNull(authorizationResponse.getAccessToken(), "The access token is null");
        assertNotNull(authorizationResponse.getState(), "The state is null");
        assertNotNull(authorizationResponse.getTokenType(), "The token type is null");
        assertNotNull(authorizationResponse.getExpiresIn(), "The expires in value is null");
        assertNotNull(authorizationResponse.getScope(), "The scope must be null");
        assertNotNull(authorizationResponse.getIdToken(), "The id token must be null");

        String accessToken = authorizationResponse.getAccessToken();

        // 3. Request user info
        UserInfoClient userInfoClient = new UserInfoClient(userInfoEndpoint);
        userInfoClient.setJwksUri(jwksUri);
        UserInfoResponse userInfoResponse = userInfoClient.execUserInfo(accessToken);

        showClient(userInfoClient);
        assertEquals(userInfoResponse.getStatus(), 200, "Unexpected response code: " + userInfoResponse.getStatus());
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.SUBJECT_IDENTIFIER));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.GIVEN_NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.FAMILY_NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.PICTURE));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.EMAIL));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.ZONEINFO));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.LOCALE));
    }

    @Parameters({"redirectUris", "redirectUri", "userId", "userSecret", "clientJwksUri",
            "RS256_modulus", "RS256_privateExponent"})
    @Test
    public void requestUserInfoAlgRSAOAEPEncA256GCM(
            final String redirectUris, final String redirectUri, final String userId, final String userSecret,
            final String jwksUri, final String modulus, final String privateExponent) throws Exception {
        showTitle("requestUserInfoAlgRSAOAEPEncA256GCM");

        List<ResponseType> responseTypes = Arrays.asList(
                ResponseType.TOKEN,
                ResponseType.ID_TOKEN);

        // 1. Dynamic Registration
        RegisterRequest registerRequest = new RegisterRequest(ApplicationType.WEB, "oxAuth test app",
                StringUtils.spaceSeparatedToList(redirectUris));
        registerRequest.setJwksUri(jwksUri);
        registerRequest.setResponseTypes(responseTypes);
        registerRequest.setUserInfoEncryptedResponseAlg(KeyEncryptionAlgorithm.RSA_OAEP);
        registerRequest.setUserInfoEncryptedResponseEnc(BlockEncryptionAlgorithm.A256GCM);
        registerRequest.addCustomAttribute("oxAuthTrustedClient", "true");

        RegisterClient registerClient = new RegisterClient(registrationEndpoint);
        registerClient.setRequest(registerRequest);
        RegisterResponse registerResponse = registerClient.exec();

        showClient(registerClient);
        assertEquals(registerResponse.getStatus(), 200, "Unexpected response code: " + registerResponse.getEntity());
        assertNotNull(registerResponse.getClientId());
        assertNotNull(registerResponse.getClientSecret());
        assertNotNull(registerResponse.getRegistrationAccessToken());
        assertNotNull(registerResponse.getClientSecretExpiresAt());

        String clientId = registerResponse.getClientId();

        // 2. Request authorization
        List<String> scopes = Arrays.asList(
                "openid",
                "profile",
                "address",
                "email");
        String nonce = UUID.randomUUID().toString();
        String state = "af0ifjsldkj";

        AuthorizationRequest authorizationRequest = new AuthorizationRequest(responseTypes, clientId, scopes, redirectUri, nonce);
        authorizationRequest.setState(state);
        authorizationRequest.setAuthUsername(userId);
        authorizationRequest.setAuthPassword(userSecret);
        authorizationRequest.getPrompts().add(Prompt.NONE);

        AuthorizeClient authorizeClient = new AuthorizeClient(authorizationEndpoint);
        authorizeClient.setRequest(authorizationRequest);
        AuthorizationResponse authorizationResponse = authorizeClient.exec();

        showClient(authorizeClient);
        assertEquals(authorizationResponse.getStatus(), 302, "Unexpected response code: " + authorizationResponse.getStatus());
        assertNotNull(authorizationResponse.getLocation(), "The location is null");
        assertNotNull(authorizationResponse.getAccessToken(), "The access token is null");
        assertNotNull(authorizationResponse.getState(), "The state is null");
        assertNotNull(authorizationResponse.getTokenType(), "The token type is null");
        assertNotNull(authorizationResponse.getExpiresIn(), "The expires in value is null");
        assertNotNull(authorizationResponse.getScope(), "The scope must be null");
        assertNotNull(authorizationResponse.getIdToken(), "The id token must be null");

        String accessToken = authorizationResponse.getAccessToken();

        // 3. Request user info (encrypted)
        RSAPrivateKey rsaPrivateKey = new RSAPrivateKey(modulus, privateExponent);

        UserInfoRequest userInfoRequest = new UserInfoRequest(accessToken);

        UserInfoClient userInfoClient = new UserInfoClient(userInfoEndpoint);
        userInfoClient.setRsaPrivateKey(rsaPrivateKey);
        userInfoClient.setRequest(userInfoRequest);
        UserInfoResponse userInfoResponse = userInfoClient.exec();

        showClient(userInfoClient);
        assertEquals(userInfoResponse.getStatus(), 200, "Unexpected response code: " + userInfoResponse.getStatus());
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.SUBJECT_IDENTIFIER));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.GIVEN_NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.FAMILY_NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.PICTURE));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.EMAIL));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.ZONEINFO));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.LOCALE));
    }

    @Parameters({"redirectUris", "redirectUri", "userId", "userSecret", "clientJwksUri",
            "RS256_modulus", "RS256_privateExponent"})
    @Test
    public void requestUserInfoAlgRSA15EncA128CBCPLUSHS256(
            final String redirectUris, final String redirectUri, final String userId, final String userSecret,
            final String jwksUri, final String modulus, final String privateExponent) throws Exception {
        showTitle("requestUserInfoAlgRSA15EncA128CBCPLUSHS256");

        List<ResponseType> responseTypes = Arrays.asList(
                ResponseType.TOKEN,
                ResponseType.ID_TOKEN);

        // 1. Dynamic Registration
        RegisterRequest registerRequest = new RegisterRequest(ApplicationType.WEB, "oxAuth test app",
                StringUtils.spaceSeparatedToList(redirectUris));
        registerRequest.setJwksUri(jwksUri);
        registerRequest.setResponseTypes(responseTypes);
        registerRequest.setUserInfoEncryptedResponseAlg(KeyEncryptionAlgorithm.RSA1_5);
        registerRequest.setUserInfoEncryptedResponseEnc(BlockEncryptionAlgorithm.A128CBC_PLUS_HS256);
        registerRequest.addCustomAttribute("oxAuthTrustedClient", "true");

        RegisterClient registerClient = new RegisterClient(registrationEndpoint);
        registerClient.setRequest(registerRequest);
        RegisterResponse registerResponse = registerClient.exec();

        showClient(registerClient);
        assertEquals(registerResponse.getStatus(), 200, "Unexpected response code: " + registerResponse.getEntity());
        assertNotNull(registerResponse.getClientId());
        assertNotNull(registerResponse.getClientSecret());
        assertNotNull(registerResponse.getRegistrationAccessToken());
        assertNotNull(registerResponse.getClientSecretExpiresAt());

        String clientId = registerResponse.getClientId();

        // 2. Request authorization
        List<String> scopes = Arrays.asList(
                "openid",
                "profile",
                "address",
                "email");
        String nonce = UUID.randomUUID().toString();
        String state = "af0ifjsldkj";

        AuthorizationRequest authorizationRequest = new AuthorizationRequest(responseTypes, clientId, scopes, redirectUri, nonce);
        authorizationRequest.setState(state);
        authorizationRequest.setAuthUsername(userId);
        authorizationRequest.setAuthPassword(userSecret);
        authorizationRequest.getPrompts().add(Prompt.NONE);

        AuthorizeClient authorizeClient = new AuthorizeClient(authorizationEndpoint);
        authorizeClient.setRequest(authorizationRequest);
        AuthorizationResponse authorizationResponse = authorizeClient.exec();

        showClient(authorizeClient);
        assertEquals(authorizationResponse.getStatus(), 302, "Unexpected response code: " + authorizationResponse.getStatus());
        assertNotNull(authorizationResponse.getLocation(), "The location is null");
        assertNotNull(authorizationResponse.getAccessToken(), "The access token is null");
        assertNotNull(authorizationResponse.getState(), "The state is null");
        assertNotNull(authorizationResponse.getTokenType(), "The token type is null");
        assertNotNull(authorizationResponse.getExpiresIn(), "The expires in value is null");
        assertNotNull(authorizationResponse.getScope(), "The scope must be null");
        assertNotNull(authorizationResponse.getIdToken(), "The id token must be null");

        String accessToken = authorizationResponse.getAccessToken();

        // 3. Request user info (encrypted)
        RSAPrivateKey rsaPrivateKey = new RSAPrivateKey(modulus, privateExponent);

        UserInfoRequest userInfoRequest = new UserInfoRequest(accessToken);

        UserInfoClient userInfoClient = new UserInfoClient(userInfoEndpoint);
        userInfoClient.setRsaPrivateKey(rsaPrivateKey);
        userInfoClient.setRequest(userInfoRequest);
        UserInfoResponse userInfoResponse = userInfoClient.exec();

        showClient(userInfoClient);
        assertEquals(userInfoResponse.getStatus(), 200, "Unexpected response code: " + userInfoResponse.getStatus());
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.SUBJECT_IDENTIFIER));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.GIVEN_NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.FAMILY_NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.PICTURE));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.EMAIL));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.ZONEINFO));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.LOCALE));
    }

    @Parameters({"redirectUris", "redirectUri", "userId", "userSecret", "clientJwksUri",
            "RS256_modulus", "RS256_privateExponent"})
    @Test
    public void requestUserInfoAlgRSA15EncA256CBCPLUSHS512(
            final String redirectUris, final String redirectUri, final String userId, final String userSecret,
            final String jwksUri, final String modulus, final String privateExponent) throws Exception {
        showTitle("requestUserInfoAlgRSA15EncA256CBCPLUSHS512");

        List<ResponseType> responseTypes = Arrays.asList(
                ResponseType.TOKEN,
                ResponseType.ID_TOKEN);

        // 1. Dynamic Registration
        RegisterRequest registerRequest = new RegisterRequest(ApplicationType.WEB, "oxAuth test app",
                StringUtils.spaceSeparatedToList(redirectUris));
        registerRequest.setJwksUri(jwksUri);
        registerRequest.setResponseTypes(responseTypes);
        registerRequest.setUserInfoEncryptedResponseAlg(KeyEncryptionAlgorithm.RSA1_5);
        registerRequest.setUserInfoEncryptedResponseEnc(BlockEncryptionAlgorithm.A256CBC_PLUS_HS512);
        registerRequest.addCustomAttribute("oxAuthTrustedClient", "true");

        RegisterClient registerClient = new RegisterClient(registrationEndpoint);
        registerClient.setRequest(registerRequest);
        RegisterResponse registerResponse = registerClient.exec();

        showClient(registerClient);
        assertEquals(registerResponse.getStatus(), 200, "Unexpected response code: " + registerResponse.getEntity());
        assertNotNull(registerResponse.getClientId());
        assertNotNull(registerResponse.getClientSecret());
        assertNotNull(registerResponse.getRegistrationAccessToken());
        assertNotNull(registerResponse.getClientSecretExpiresAt());

        String clientId = registerResponse.getClientId();

        // 2. Request authorization
        List<String> scopes = Arrays.asList(
                "openid",
                "profile",
                "address",
                "email");
        String nonce = UUID.randomUUID().toString();
        String state = "af0ifjsldkj";

        AuthorizationRequest authorizationRequest = new AuthorizationRequest(responseTypes, clientId, scopes, redirectUri, nonce);
        authorizationRequest.setState(state);
        authorizationRequest.setAuthUsername(userId);
        authorizationRequest.setAuthPassword(userSecret);
        authorizationRequest.getPrompts().add(Prompt.NONE);

        AuthorizeClient authorizeClient = new AuthorizeClient(authorizationEndpoint);
        authorizeClient.setRequest(authorizationRequest);
        AuthorizationResponse authorizationResponse = authorizeClient.exec();

        showClient(authorizeClient);
        assertEquals(authorizationResponse.getStatus(), 302, "Unexpected response code: " + authorizationResponse.getStatus());
        assertNotNull(authorizationResponse.getLocation(), "The location is null");
        assertNotNull(authorizationResponse.getAccessToken(), "The access token is null");
        assertNotNull(authorizationResponse.getState(), "The state is null");
        assertNotNull(authorizationResponse.getTokenType(), "The token type is null");
        assertNotNull(authorizationResponse.getExpiresIn(), "The expires in value is null");
        assertNotNull(authorizationResponse.getScope(), "The scope must be null");
        assertNotNull(authorizationResponse.getIdToken(), "The id token must be null");

        String accessToken = authorizationResponse.getAccessToken();

        // 3. Request user info (encrypted)
        RSAPrivateKey rsaPrivateKey = new RSAPrivateKey(modulus, privateExponent);

        UserInfoRequest userInfoRequest = new UserInfoRequest(accessToken);

        UserInfoClient userInfoClient = new UserInfoClient(userInfoEndpoint);
        userInfoClient.setRsaPrivateKey(rsaPrivateKey);
        userInfoClient.setRequest(userInfoRequest);
        UserInfoResponse userInfoResponse = userInfoClient.exec();

        showClient(userInfoClient);
        assertEquals(userInfoResponse.getStatus(), 200, "Unexpected response code: " + userInfoResponse.getStatus());
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.SUBJECT_IDENTIFIER));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.GIVEN_NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.FAMILY_NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.PICTURE));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.EMAIL));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.ZONEINFO));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.LOCALE));
    }

    @Parameters({"redirectUris", "redirectUri", "userId", "userSecret"})
    @Test
    public void requestUserInfoAlgA128KWEncA128GCM(final String redirectUris, final String redirectUri,
                                                   final String userId, final String userSecret) throws Exception {
        showTitle("requestUserInfoAlgA128KWEncA128GCM");

        List<ResponseType> responseTypes = Arrays.asList(
                ResponseType.TOKEN,
                ResponseType.ID_TOKEN);

        // 1. Dynamic Registration
        RegisterRequest registerRequest = new RegisterRequest(ApplicationType.WEB, "oxAuth test app",
                StringUtils.spaceSeparatedToList(redirectUris));
        registerRequest.setResponseTypes(responseTypes);
        registerRequest.setUserInfoEncryptedResponseAlg(KeyEncryptionAlgorithm.A128KW);
        registerRequest.setUserInfoEncryptedResponseEnc(BlockEncryptionAlgorithm.A128GCM);
        registerRequest.addCustomAttribute("oxAuthTrustedClient", "true");

        RegisterClient registerClient = new RegisterClient(registrationEndpoint);
        registerClient.setRequest(registerRequest);
        RegisterResponse registerResponse = registerClient.exec();

        showClient(registerClient);
        assertEquals(registerResponse.getStatus(), 200, "Unexpected response code: " + registerResponse.getEntity());
        assertNotNull(registerResponse.getClientId());
        assertNotNull(registerResponse.getClientSecret());
        assertNotNull(registerResponse.getRegistrationAccessToken());
        assertNotNull(registerResponse.getClientSecretExpiresAt());

        String clientId = registerResponse.getClientId();
        String clientSecret = registerResponse.getClientSecret();

        // 2. Request authorization
        List<String> scopes = Arrays.asList(
                "openid",
                "profile",
                "address",
                "email");
        String nonce = UUID.randomUUID().toString();
        String state = "af0ifjsldkj";

        AuthorizationRequest authorizationRequest = new AuthorizationRequest(responseTypes, clientId, scopes, redirectUri, nonce);
        authorizationRequest.setState(state);
        authorizationRequest.setAuthUsername(userId);
        authorizationRequest.setAuthPassword(userSecret);
        authorizationRequest.getPrompts().add(Prompt.NONE);

        AuthorizeClient authorizeClient = new AuthorizeClient(authorizationEndpoint);
        authorizeClient.setRequest(authorizationRequest);
        AuthorizationResponse authorizationResponse = authorizeClient.exec();

        showClient(authorizeClient);
        assertEquals(authorizationResponse.getStatus(), 302, "Unexpected response code: " + authorizationResponse.getStatus());
        assertNotNull(authorizationResponse.getLocation(), "The location is null");
        assertNotNull(authorizationResponse.getAccessToken(), "The access token is null");
        assertNotNull(authorizationResponse.getState(), "The state is null");
        assertNotNull(authorizationResponse.getTokenType(), "The token type is null");
        assertNotNull(authorizationResponse.getExpiresIn(), "The expires in value is null");
        assertNotNull(authorizationResponse.getScope(), "The scope must be null");
        assertNotNull(authorizationResponse.getIdToken(), "The id token must be null");

        String accessToken = authorizationResponse.getAccessToken();

        // 3. Request user info (encrypted)
        UserInfoRequest userInfoRequest = new UserInfoRequest(accessToken);

        UserInfoClient userInfoClient = new UserInfoClient(userInfoEndpoint);
        userInfoClient.setSharedKey(clientSecret);
        userInfoClient.setRequest(userInfoRequest);
        UserInfoResponse userInfoResponse = userInfoClient.exec();

        showClient(userInfoClient);
        assertEquals(userInfoResponse.getStatus(), 200, "Unexpected response code: " + userInfoResponse.getStatus());
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.SUBJECT_IDENTIFIER));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.GIVEN_NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.FAMILY_NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.PICTURE));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.EMAIL));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.ZONEINFO));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.LOCALE));
    }

    @Parameters({"redirectUris", "redirectUri", "userId", "userSecret"})
    @Test
    public void requestUserInfoAlgA256KWEncA256GCM(
            final String redirectUris, final String redirectUri, final String userId, final String userSecret) throws Exception {
        showTitle("requestUserInfoAlgA256KWEncA256GCM");

        List<ResponseType> responseTypes = Arrays.asList(
                ResponseType.TOKEN,
                ResponseType.ID_TOKEN);

        // 1. Dynamic Registration
        RegisterRequest registerRequest = new RegisterRequest(ApplicationType.WEB, "oxAuth test app",
                StringUtils.spaceSeparatedToList(redirectUris));
        registerRequest.setResponseTypes(responseTypes);
        registerRequest.setUserInfoEncryptedResponseAlg(KeyEncryptionAlgorithm.A256KW);
        registerRequest.setUserInfoEncryptedResponseEnc(BlockEncryptionAlgorithm.A256GCM);
        registerRequest.addCustomAttribute("oxAuthTrustedClient", "true");

        RegisterClient registerClient = new RegisterClient(registrationEndpoint);
        registerClient.setRequest(registerRequest);
        RegisterResponse registerResponse = registerClient.exec();

        showClient(registerClient);
        assertEquals(registerResponse.getStatus(), 200, "Unexpected response code: " + registerResponse.getEntity());
        assertNotNull(registerResponse.getClientId());
        assertNotNull(registerResponse.getClientSecret());
        assertNotNull(registerResponse.getRegistrationAccessToken());
        assertNotNull(registerResponse.getClientSecretExpiresAt());

        String clientId = registerResponse.getClientId();
        String clientSecret = registerResponse.getClientSecret();

        // 2. Request authorization
        List<String> scopes = Arrays.asList(
                "openid",
                "profile",
                "address",
                "email");
        String nonce = UUID.randomUUID().toString();
        String state = "af0ifjsldkj";

        AuthorizationRequest authorizationRequest = new AuthorizationRequest(responseTypes, clientId, scopes, redirectUri, nonce);
        authorizationRequest.setState(state);
        authorizationRequest.setAuthUsername(userId);
        authorizationRequest.setAuthPassword(userSecret);
        authorizationRequest.getPrompts().add(Prompt.NONE);

        AuthorizeClient authorizeClient = new AuthorizeClient(authorizationEndpoint);
        authorizeClient.setRequest(authorizationRequest);
        AuthorizationResponse authorizationResponse = authorizeClient.exec();

        showClient(authorizeClient);
        assertEquals(authorizationResponse.getStatus(), 302, "Unexpected response code: " + authorizationResponse.getStatus());
        assertNotNull(authorizationResponse.getLocation(), "The location is null");
        assertNotNull(authorizationResponse.getAccessToken(), "The access token is null");
        assertNotNull(authorizationResponse.getState(), "The state is null");
        assertNotNull(authorizationResponse.getTokenType(), "The token type is null");
        assertNotNull(authorizationResponse.getExpiresIn(), "The expires in value is null");
        assertNotNull(authorizationResponse.getScope(), "The scope must be null");
        assertNotNull(authorizationResponse.getIdToken(), "The id token must be null");

        String accessToken = authorizationResponse.getAccessToken();

        // 3. Request user info (encrypted)
        UserInfoRequest userInfoRequest = new UserInfoRequest(accessToken);

        UserInfoClient userInfoClient = new UserInfoClient(userInfoEndpoint);
        userInfoClient.setSharedKey(clientSecret);
        userInfoClient.setRequest(userInfoRequest);
        UserInfoResponse userInfoResponse = userInfoClient.exec();

        showClient(userInfoClient);
        assertEquals(userInfoResponse.getStatus(), 200, "Unexpected response code: " + userInfoResponse.getStatus());
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.SUBJECT_IDENTIFIER));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.GIVEN_NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.FAMILY_NAME));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.PICTURE));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.EMAIL));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.ZONEINFO));
        assertNotNull(userInfoResponse.getClaim(JwtClaimName.LOCALE));
    }
}
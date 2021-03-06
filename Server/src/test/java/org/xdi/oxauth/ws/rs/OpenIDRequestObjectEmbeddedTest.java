/*
 * oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2014, Gluu
 */

package org.xdi.oxauth.ws.rs;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;
import static org.xdi.oxauth.model.register.RegisterResponseParam.CLIENT_ID_ISSUED_AT;
import static org.xdi.oxauth.model.register.RegisterResponseParam.CLIENT_SECRET;
import static org.xdi.oxauth.model.register.RegisterResponseParam.CLIENT_SECRET_EXPIRES_AT;
import static org.xdi.oxauth.model.register.RegisterResponseParam.REGISTRATION_ACCESS_TOKEN;
import static org.xdi.oxauth.model.register.RegisterResponseParam.REGISTRATION_CLIENT_URI;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.MediaType;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.jboss.seam.mock.EnhancedMockHttpServletRequest;
import org.jboss.seam.mock.EnhancedMockHttpServletResponse;
import org.jboss.seam.mock.ResourceRequestEnvironment;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;
import org.xdi.oxauth.BaseTest;
import org.xdi.oxauth.client.AuthorizationRequest;
import org.xdi.oxauth.client.QueryStringDecoder;
import org.xdi.oxauth.client.RegisterRequest;
import org.xdi.oxauth.client.UserInfoRequest;
import org.xdi.oxauth.client.model.authorize.Claim;
import org.xdi.oxauth.client.model.authorize.ClaimValue;
import org.xdi.oxauth.client.model.authorize.JwtAuthorizationRequest;
import org.xdi.oxauth.model.common.AuthorizationMethod;
import org.xdi.oxauth.model.common.Prompt;
import org.xdi.oxauth.model.common.ResponseType;
import org.xdi.oxauth.model.crypto.signature.SignatureAlgorithm;
import org.xdi.oxauth.model.jwt.JwtClaimName;
import org.xdi.oxauth.model.register.ApplicationType;
import org.xdi.oxauth.model.register.RegisterResponseParam;
import org.xdi.oxauth.model.util.JwtUtil;
import org.xdi.oxauth.model.util.StringUtils;

/**
 * Functional tests for OpenID Request Object (embedded)
 *
 * @author Javier Rojas Blum Date: 09.04.2012
 */
public class OpenIDRequestObjectEmbeddedTest extends BaseTest {

    private String idToken1;
    private String accessToken1;
    private String accessToken2;
    private String clientId1;
    private String clientSecret1;
    private String clientId2;
    private String clientSecret2;
    private String clientId3;

    @Parameters({"registerPath", "redirectUris"})
    @Test
    public void requestParameterMethod1Step1(final String registerPath, final String redirectUris) throws Exception {
        new ResourceRequestEnvironment.ResourceRequest(new ResourceRequestEnvironment(this),
                ResourceRequestEnvironment.Method.POST, registerPath) {

            @Override
            protected void prepareRequest(EnhancedMockHttpServletRequest request) {
                try {
                    super.prepareRequest(request);

                    List<ResponseType> responseTypes = Arrays.asList(
                            ResponseType.TOKEN,
                            ResponseType.ID_TOKEN);

                    RegisterRequest registerRequest = new RegisterRequest(ApplicationType.WEB, "oxAuth test app",
                            StringUtils.spaceSeparatedToList(redirectUris));
                    registerRequest.setResponseTypes(responseTypes);
                    registerRequest.setRequestObjectSigningAlg(SignatureAlgorithm.HS256);
                    registerRequest.addCustomAttribute("oxAuthTrustedClient", "true");

                    request.setContentType(MediaType.APPLICATION_JSON);
                    String registerRequestContent = registerRequest.getJSONParameters().toString(4);
                    request.setContent(registerRequestContent.getBytes());
                } catch (JSONException e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                }
            }

            @Override
            protected void onResponse(EnhancedMockHttpServletResponse response) {
                super.onResponse(response);
                showResponse("requestParameterMethod1Step1", response);

                assertEquals(response.getStatus(), 200, "Unexpected response code. " + response.getContentAsString());
                assertNotNull(response.getContentAsString(), "Unexpected result: " + response.getContentAsString());
                try {
                    JSONObject jsonObj = new JSONObject(response.getContentAsString());
                    assertTrue(jsonObj.has(RegisterResponseParam.CLIENT_ID.toString()));
                    assertTrue(jsonObj.has(CLIENT_SECRET.toString()));
                    assertTrue(jsonObj.has(REGISTRATION_ACCESS_TOKEN.toString()));
                    assertTrue(jsonObj.has(REGISTRATION_CLIENT_URI.toString()));
                    assertTrue(jsonObj.has(CLIENT_ID_ISSUED_AT.toString()));
                    assertTrue(jsonObj.has(CLIENT_SECRET_EXPIRES_AT.toString()));

                    clientId1 = jsonObj.getString(RegisterResponseParam.CLIENT_ID.toString());
                    clientSecret1 = jsonObj.getString(CLIENT_SECRET.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                    fail(e.getMessage() + "\nResponse was: " + response.getContentAsString());
                }
            }
        }.run();
    }

    @Parameters({"authorizePath", "userId", "userSecret", "redirectUri"})
    @Test(dependsOnMethods = "requestParameterMethod1Step1")
    public void requestParameterMethod1Step2(final String authorizePath,
                                             final String userId, final String userSecret,
                                             final String redirectUri) throws Exception {
        new ResourceRequestEnvironment.ResourceRequest(new ResourceRequestEnvironment(this),
                ResourceRequestEnvironment.Method.GET, authorizePath) {

            @Override
            protected void prepareRequest(EnhancedMockHttpServletRequest request) {
                super.prepareRequest(request);

                List<ResponseType> responseTypes = Arrays.asList(
                        ResponseType.TOKEN,
                        ResponseType.ID_TOKEN);
                List<String> scopes = Arrays.asList(
                        "openid",
                        "profile",
                        "address",
                        "email");
                String nonce = UUID.randomUUID().toString();
                String state = "af0ifjsldkj";

                AuthorizationRequest authorizationRequest = new AuthorizationRequest(
                        responseTypes, clientId1, scopes, redirectUri, nonce);
                authorizationRequest.setState(state);
                authorizationRequest.getPrompts().add(Prompt.NONE);
                authorizationRequest.setAuthUsername(userId);
                authorizationRequest.setAuthPassword(userSecret);

                JwtAuthorizationRequest jwtAuthorizationRequest = new JwtAuthorizationRequest(authorizationRequest, SignatureAlgorithm.HS256, clientSecret1);
                jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.NAME, ClaimValue.createNull()));
                jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.NICKNAME, ClaimValue.createEssential(false)));
                jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.EMAIL, ClaimValue.createNull()));
                jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.EMAIL_VERIFIED, ClaimValue.createNull()));
                jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.PICTURE, ClaimValue.createEssential(false)));
                jwtAuthorizationRequest.addIdTokenClaim(new Claim(JwtClaimName.AUTHENTICATION_TIME, ClaimValue.createNull()));
                jwtAuthorizationRequest.addIdTokenClaim(new Claim(JwtClaimName.AUTHENTICATION_CONTEXT_CLASS_REFERENCE, ClaimValue.createValueList(new String[]{"2"})));
                String authJwt = jwtAuthorizationRequest.getEncodedJwt();
                authorizationRequest.setRequest(authJwt);
                System.out.println("Request JWT: " + authJwt);

                request.addHeader("Authorization", "Basic " + authorizationRequest.getEncodedCredentials());
                request.addHeader("Accept", MediaType.TEXT_PLAIN);
                request.setQueryString(authorizationRequest.getQueryString());
            }

            @Override
            protected void onResponse(EnhancedMockHttpServletResponse response) {
                super.onResponse(response);
                showResponse("requestParameterMethod1Step2", response);

                assertEquals(response.getStatus(), 302, "Unexpected response code.");
                assertNotNull(response.getHeader("Location"), "Unexpected result: " + response.getHeader("Location"));

                try {
                    URI uri = new URI(response.getHeader("Location").toString());
                    assertNotNull(uri.getQuery(), "Query string is null");

                    Map<String, String> params = QueryStringDecoder.decode(uri.getFragment());

                    assertNotNull(params.get("access_token"), "The accessToken is null");
                    assertNotNull(params.get("id_token"), "The idToken is null");
                    assertNotNull(params.get("scope"), "The scope is null");
                    assertNotNull(params.get("state"), "The state is null");

                    idToken1 = params.get("id_token");
                    accessToken1 = params.get("access_token");
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                    fail("Response URI is not well formed");
                }
            }
        }.run();
    }

    @Parameters({"userInfoPath"})
    @Test(dependsOnMethods = {"requestParameterMethod1Step2"})
    public void requestParameterMethodUserInfo(final String userInfoPath) throws Exception {
        new ResourceRequestEnvironment.ResourceRequest(new ResourceRequestEnvironment(this),
                ResourceRequestEnvironment.Method.POST, userInfoPath) {

            @Override
            protected void prepareRequest(EnhancedMockHttpServletRequest request) {
                super.prepareRequest(request);
                request.addHeader("Content-Type", MediaType.APPLICATION_FORM_URLENCODED);

                UserInfoRequest userInfoRequest = new UserInfoRequest(accessToken1);
                userInfoRequest.setAuthorizationMethod(AuthorizationMethod.FORM_ENCODED_BODY_PARAMETER);

                request.addParameters(userInfoRequest.getParameters());
            }

            @Override
            protected void onResponse(EnhancedMockHttpServletResponse response) {
                super.onResponse(response);
                showResponse("requestParameterMethodUserInfo", response);

                assertEquals(response.getStatus(), 200, "Unexpected response code.");
                assertTrue(response.getHeader("Cache-Control") != null && response.getHeader("Cache-Control").equals(
                        "no-store, private"), "Unexpected result: " + response.getHeader("Cache-Control"));
                assertTrue(response.getHeader("Pragma") != null && response.getHeader("Pragma").equals("no-cache"),
                        "Unexpected result: " + response.getHeader("Pragma"));
                assertNotNull(response.getContentAsString(), "Unexpected result: " + response.getContentAsString());
                try {
                    JSONObject jsonObj = new JSONObject(response.getContentAsString());
                    assertTrue(jsonObj.has(JwtClaimName.SUBJECT_IDENTIFIER));
                    assertTrue(jsonObj.has(JwtClaimName.NAME));
                    assertTrue(jsonObj.has(JwtClaimName.GIVEN_NAME));
                    assertTrue(jsonObj.has(JwtClaimName.FAMILY_NAME));
                    assertTrue(jsonObj.has(JwtClaimName.EMAIL));
                } catch (JSONException e) {
                    e.printStackTrace();
                    fail(e.getMessage() + "\nResponse was: " + response.getContentAsString());
                } catch (Exception e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                }
            }
        }.run();
    }

    @Parameters({"registerPath", "redirectUris"})
    @Test
    public void requestParameterMethod2Step1(final String registerPath, final String redirectUris) throws Exception {
        new ResourceRequestEnvironment.ResourceRequest(new ResourceRequestEnvironment(this),
                ResourceRequestEnvironment.Method.POST, registerPath) {

            @Override
            protected void prepareRequest(EnhancedMockHttpServletRequest request) {
                try {
                    super.prepareRequest(request);

                    List<ResponseType> responseTypes = Arrays.asList(ResponseType.TOKEN);

                    RegisterRequest registerRequest = new RegisterRequest(ApplicationType.WEB, "oxAuth test app",
                            StringUtils.spaceSeparatedToList(redirectUris));
                    registerRequest.setResponseTypes(responseTypes);
                    registerRequest.setRequestObjectSigningAlg(SignatureAlgorithm.HS256);
                    registerRequest.addCustomAttribute("oxAuthTrustedClient", "true");

                    request.setContentType(MediaType.APPLICATION_JSON);
                    String registerRequestContent = registerRequest.getJSONParameters().toString(4);
                    request.setContent(registerRequestContent.getBytes());
                } catch (JSONException e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                }
            }

            @Override
            protected void onResponse(EnhancedMockHttpServletResponse response) {
                super.onResponse(response);
                showResponse("requestParameterMethod2Step1", response);

                assertEquals(response.getStatus(), 200, "Unexpected response code. " + response.getContentAsString());
                assertNotNull(response.getContentAsString(), "Unexpected result: " + response.getContentAsString());
                try {
                    JSONObject jsonObj = new JSONObject(response.getContentAsString());
                    assertTrue(jsonObj.has(RegisterResponseParam.CLIENT_ID.toString()));
                    assertTrue(jsonObj.has(CLIENT_SECRET.toString()));
                    assertTrue(jsonObj.has(REGISTRATION_ACCESS_TOKEN.toString()));
                    assertTrue(jsonObj.has(REGISTRATION_CLIENT_URI.toString()));
                    assertTrue(jsonObj.has(CLIENT_ID_ISSUED_AT.toString()));
                    assertTrue(jsonObj.has(CLIENT_SECRET_EXPIRES_AT.toString()));

                    clientId2 = jsonObj.getString(RegisterResponseParam.CLIENT_ID.toString());
                    clientSecret2 = jsonObj.getString(CLIENT_SECRET.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                    fail(e.getMessage() + "\nResponse was: " + response.getContentAsString());
                }
            }
        }.run();
    }

    @Parameters({"authorizePath", "userId", "userSecret", "redirectUri"})
    @Test(dependsOnMethods = "requestParameterMethod2Step1")
    public void requestParameterMethod2Step2(final String authorizePath,
                                             final String userId, final String userSecret,
                                             final String redirectUri) throws Exception {
        new ResourceRequestEnvironment.ResourceRequest(new ResourceRequestEnvironment(this), ResourceRequestEnvironment.Method.GET, authorizePath) {

            @Override
            protected void prepareRequest(EnhancedMockHttpServletRequest request) {
                super.prepareRequest(request);

                List<ResponseType> responseTypes = Arrays.asList(ResponseType.TOKEN);
                List<String> scopes = Arrays.asList("openid");
                String nonce = UUID.randomUUID().toString();
                String state = "STATE0";

                AuthorizationRequest authorizationRequest = new AuthorizationRequest(
                        responseTypes, clientId2, scopes, redirectUri, nonce);
                authorizationRequest.setState(state);
                authorizationRequest.getPrompts().add(Prompt.NONE);
                authorizationRequest.setAuthUsername(userId);
                authorizationRequest.setAuthPassword(userSecret);

                JwtAuthorizationRequest jwtAuthorizationRequest = new JwtAuthorizationRequest(authorizationRequest, SignatureAlgorithm.HS256, clientSecret2);
                jwtAuthorizationRequest.getIdTokenMember().setMaxAge(86400);
                jwtAuthorizationRequest.addUserInfoClaim(new Claim("name", ClaimValue.createEssential(true)));
                String authJwt = jwtAuthorizationRequest.getEncodedJwt();
                authorizationRequest.setRequest(authJwt);
                System.out.println("Request JWT: " + authJwt);

                request.addHeader("Authorization", "Basic " + authorizationRequest.getEncodedCredentials());
                request.addHeader("Accept", MediaType.TEXT_PLAIN);
                request.setQueryString(authorizationRequest.getQueryString());
            }

            @Override
            protected void onResponse(EnhancedMockHttpServletResponse response) {
                super.onResponse(response);
                showResponse("requestParameterMethod2Step2", response);

                assertEquals(response.getStatus(), 302, "Unexpected response code.");
                assertNotNull(response.getHeader("Location"), "Unexpected result: " + response.getHeader("Location"));

                try {
                    URI uri = new URI(response.getHeader("Location").toString());
                    assertNotNull(uri.getQuery(), "Query string is null");

                    Map<String, String> params = QueryStringDecoder.decode(uri.getFragment());

                    assertNotNull(params.get("access_token"), "The accessToken is null");
                    assertNotNull(params.get("scope"), "The scope is null");
                    assertNotNull(params.get("state"), "The state is null");

                    accessToken2 = params.get("access_token");
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                    fail("Response URI is not well formed");
                }
            }
        }.run();
    }

    @Parameters({"userInfoPath"})
    @Test(dependsOnMethods = {"requestParameterMethod2Step2"})
    public void requestParameterMethod2Step3(final String userInfoPath) throws Exception {
        new ResourceRequestEnvironment.ResourceRequest(new ResourceRequestEnvironment(this),
                ResourceRequestEnvironment.Method.POST, userInfoPath) {

            @Override
            protected void prepareRequest(EnhancedMockHttpServletRequest request) {
                super.prepareRequest(request);
                request.addHeader("Content-Type", MediaType.APPLICATION_FORM_URLENCODED);

                UserInfoRequest userInfoRequest = new UserInfoRequest(accessToken2);
                userInfoRequest.setAuthorizationMethod(AuthorizationMethod.FORM_ENCODED_BODY_PARAMETER);

                request.addParameters(userInfoRequest.getParameters());
            }

            @Override
            protected void onResponse(EnhancedMockHttpServletResponse response) {
                super.onResponse(response);
                showResponse("requestParameterMethod2Step3", response);

                assertEquals(response.getStatus(), 200, "Unexpected response code.");
                assertTrue(response.getHeader("Cache-Control") != null && response.getHeader("Cache-Control").equals(
                        "no-store, private"), "Unexpected result: " + response.getHeader("Cache-Control"));
                assertTrue(response.getHeader("Pragma") != null && response.getHeader("Pragma").equals("no-cache"),
                        "Unexpected result: " + response.getHeader("Pragma"));
                assertNotNull(response.getContentAsString(), "Unexpected result: " + response.getContentAsString());
                try {
                    JSONObject jsonObj = new JSONObject(response.getContentAsString());
                    assertTrue(jsonObj.has(JwtClaimName.SUBJECT_IDENTIFIER));
                    assertTrue(jsonObj.has(JwtClaimName.NAME));
                } catch (JSONException e) {
                    e.printStackTrace();
                    fail(e.getMessage() + "\nResponse was: " + response.getContentAsString());
                } catch (Exception e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                }
            }
        }.run();
    }

    @Parameters({"authorizePath", "userId", "userSecret", "clientId", "redirectUri"})
    @Test
    public void requestParameterMethodFail1(final String authorizePath, final String userId, final String userSecret, final String clientId,
                                            final String redirectUri) throws Exception {
        new ResourceRequestEnvironment.ResourceRequest(new ResourceRequestEnvironment(this), ResourceRequestEnvironment.Method.GET, authorizePath) {

            @Override
            protected void prepareRequest(EnhancedMockHttpServletRequest request) {
                super.prepareRequest(request);

                List<ResponseType> responseTypes = Arrays.asList(ResponseType.TOKEN, ResponseType.ID_TOKEN);
                List<String> scopes = Arrays.asList("openid", "profile", "address", "email");
                String nonce = UUID.randomUUID().toString();
                String state = "af0ifjsldkj";

                AuthorizationRequest authorizationRequest = new AuthorizationRequest(
                        responseTypes, clientId, scopes, redirectUri, nonce);
                authorizationRequest.setState(state);
                authorizationRequest.setRequest("INVALID_OPENID_REQUEST_OBJECT");
                authorizationRequest.setAuthUsername(userId);
                authorizationRequest.setAuthPassword(userSecret);

                request.addHeader("Authorization", "Basic " + authorizationRequest.getEncodedCredentials());
                request.addHeader("Accept", MediaType.TEXT_PLAIN);
                request.setQueryString(authorizationRequest.getQueryString());
            }

            @Override
            protected void onResponse(EnhancedMockHttpServletResponse response) {
                super.onResponse(response);
                showResponse("requestParameterMethodFail1 (Invalid OpenID Request Object)", response);

                assertEquals(response.getStatus(), 302, "Unexpected response code.");
                assertNotNull(response.getHeader("Location"), "Unexpected result: " + response.getHeader("Location"));

                if (response.getHeader("Location") != null) {
                    try {
                        URI uri = new URI(response.getHeader("Location").toString());
                        assertNotNull(uri.getFragment(), "Fragment is null");

                        Map<String, String> params = QueryStringDecoder.decode(uri.getFragment());

                        assertNotNull(params.get("error"), "The error value is null");
                        assertNotNull(params.get("error_description"), "The errorDescription value is null");
                        assertNotNull(params.get("state"), "The state is null");
                    } catch (URISyntaxException e) {
                        e.printStackTrace();
                        fail("Response URI is not well formed");
                    }
                }
            }
        }.run();
    }

    @Parameters({"authorizePath", "userId", "userSecret", "clientId", "clientSecret", "redirectUri"})
    @Test
    public void requestParameterMethodFail2(final String authorizePath,
                                            final String userId, final String userSecret,
                                            final String clientId, final String clientSecret,
                                            final String redirectUri) throws Exception {
        new ResourceRequestEnvironment.ResourceRequest(new ResourceRequestEnvironment(this), ResourceRequestEnvironment.Method.GET, authorizePath) {

            @Override
            protected void prepareRequest(EnhancedMockHttpServletRequest request) {
                super.prepareRequest(request);

                List<ResponseType> responseTypes = Arrays.asList(ResponseType.TOKEN, ResponseType.ID_TOKEN);
                List<String> scopes = Arrays.asList("openid", "profile", "address", "email");
                String nonce = UUID.randomUUID().toString();
                String state = "af0ifjsldkj";

                AuthorizationRequest authorizationRequest = new AuthorizationRequest(
                        responseTypes, clientId, scopes, redirectUri, nonce);
                authorizationRequest.setState(state);
                authorizationRequest.setAuthUsername(userId);
                authorizationRequest.setAuthPassword(userSecret);

                JwtAuthorizationRequest jwtAuthorizationRequest = new JwtAuthorizationRequest(authorizationRequest, SignatureAlgorithm.HS256, clientSecret);
                jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.NAME, ClaimValue.createNull()));
                jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.NICKNAME, ClaimValue.createEssential(false)));
                jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.EMAIL, ClaimValue.createNull()));
                jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.EMAIL_VERIFIED, ClaimValue.createNull()));
                jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.PICTURE, ClaimValue.createEssential(false)));
                jwtAuthorizationRequest.addIdTokenClaim(new Claim(JwtClaimName.AUTHENTICATION_TIME, ClaimValue.createNull()));
                jwtAuthorizationRequest.addIdTokenClaim(new Claim(JwtClaimName.AUTHENTICATION_CONTEXT_CLASS_REFERENCE, ClaimValue.createValueList(new String[]{"2"})));
                jwtAuthorizationRequest.getIdTokenMember().setMaxAge(86400);
                String authJwt = jwtAuthorizationRequest.getEncodedJwt();
                authorizationRequest.setRequest(authJwt + "INVALID_SIGNATURE");
                System.out.println("Request JWT: " + authJwt);

                request.addHeader("Authorization", "Basic " + authorizationRequest.getEncodedCredentials());
                request.addHeader("Accept", MediaType.TEXT_PLAIN);
                request.setQueryString(authorizationRequest.getQueryString());
            }

            @Override
            protected void onResponse(EnhancedMockHttpServletResponse response) {
                super.onResponse(response);
                showResponse("requestParameterMethodFail2 (Invalid OpenID Request Object, due to invalid signature)", response);

                assertEquals(response.getStatus(), 302, "Unexpected response code.");
                assertNotNull(response.getHeader("Location"), "Unexpected result: " + response.getHeader("Location"));

                if (response.getHeader("Location") != null) {
                    try {
                        URI uri = new URI(response.getHeader("Location").toString());
                        assertNotNull(uri.getFragment(), "Fragment is null");

                        Map<String, String> params = QueryStringDecoder.decode(uri.getFragment());

                        assertNotNull(params.get("error"), "The error value is null");
                        assertNotNull(params.get("error_description"), "The errorDescription value is null");
                        assertNotNull(params.get("state"), "The state is null");
                    } catch (URISyntaxException e) {
                        e.printStackTrace();
                        fail("Response URI is not well formed");
                    }
                }
            }
        }.run();
    }

    @Parameters({"authorizePath", "userId", "userSecret", "clientId", "clientSecret", "redirectUri"})
    @Test
    public void requestParameterMethodFail3(final String authorizePath,
                                            final String userId, final String userSecret,
                                            final String clientId, final String clientSecret,
                                            final String redirectUri) throws Exception {
        new ResourceRequestEnvironment.ResourceRequest(new ResourceRequestEnvironment(this), ResourceRequestEnvironment.Method.GET, authorizePath) {

            @Override
            protected void prepareRequest(EnhancedMockHttpServletRequest request) {
                super.prepareRequest(request);

                List<ResponseType> responseTypes = Arrays.asList(ResponseType.TOKEN, ResponseType.ID_TOKEN);
                List<String> scopes = Arrays.asList("openid", "profile", "address", "email");
                String nonce = UUID.randomUUID().toString();
                String state = "af0ifjsldkj";

                AuthorizationRequest authorizationRequest = new AuthorizationRequest(
                        responseTypes, clientId, scopes, redirectUri, nonce);
                authorizationRequest.setState(state);
                authorizationRequest.setAuthUsername(userId);
                authorizationRequest.setAuthPassword(userSecret);

                JwtAuthorizationRequest jwtAuthorizationRequest = new JwtAuthorizationRequest(authorizationRequest, SignatureAlgorithm.HS256, clientSecret);
                jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.NAME, ClaimValue.createNull()));
                jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.NICKNAME, ClaimValue.createEssential(false)));
                jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.EMAIL, ClaimValue.createNull()));
                jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.EMAIL_VERIFIED, ClaimValue.createNull()));
                jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.PICTURE, ClaimValue.createEssential(false)));
                jwtAuthorizationRequest.addIdTokenClaim(new Claim(JwtClaimName.AUTHENTICATION_TIME, ClaimValue.createNull()));
                jwtAuthorizationRequest.addIdTokenClaim(new Claim(JwtClaimName.AUTHENTICATION_CONTEXT_CLASS_REFERENCE, ClaimValue.createValueList(new String[]{"2"})));
                jwtAuthorizationRequest.getIdTokenMember().setMaxAge(86400);
                jwtAuthorizationRequest.setClientId("INVALID_CLIENT_ID");
                String authJwt = jwtAuthorizationRequest.getEncodedJwt();
                authorizationRequest.setRequest(authJwt);
                System.out.println("Request JWT: " + authJwt);

                request.addHeader("Authorization", "Basic " + authorizationRequest.getEncodedCredentials());
                request.addHeader("Accept", MediaType.TEXT_PLAIN);
                request.setQueryString(authorizationRequest.getQueryString());
            }

            @Override
            protected void onResponse(EnhancedMockHttpServletResponse response) {
                super.onResponse(response);
                showResponse("requestParameterMethodFail3 (Invalid OpenID Request Object)", response);

                assertEquals(response.getStatus(), 302, "Unexpected response code.");
                assertNotNull(response.getHeader("Location"), "Unexpected result: " + response.getHeader("Location"));

                if (response.getHeader("Location") != null) {
                    try {
                        URI uri = new URI(response.getHeader("Location").toString());
                        assertNotNull(uri.getFragment(), "Fragment is null");

                        Map<String, String> params = QueryStringDecoder.decode(uri.getFragment());

                        assertNotNull(params.get("error"), "The error value is null");
                        assertNotNull(params.get("error_description"), "The errorDescription value is null");
                        assertNotNull(params.get("state"), "The state is null");
                    } catch (URISyntaxException e) {
                        e.printStackTrace();
                        fail("Response URI is not well formed");
                    }
                }
            }
        }.run();
    }

    @Parameters({"authorizePath", "userId", "userSecret", "clientId", "clientSecret", "redirectUri"})
    @Test
    public void requestParameterMethodFail4(final String authorizePath,
                                            final String userId, final String userSecret,
                                            final String clientId, final String clientSecret,
                                            final String redirectUri) throws Exception {
        new ResourceRequestEnvironment.ResourceRequest(new ResourceRequestEnvironment(this), ResourceRequestEnvironment.Method.GET, authorizePath) {

            @Override
            protected void prepareRequest(EnhancedMockHttpServletRequest request) {
                super.prepareRequest(request);

                List<ResponseType> responseTypes = Arrays.asList(ResponseType.TOKEN);
                List<String> scopes = Arrays.asList("openid");
                String nonce = UUID.randomUUID().toString();
                String state = "af0ifjsldkj";

                AuthorizationRequest authorizationRequest = new AuthorizationRequest(
                        responseTypes, clientId, scopes, redirectUri, nonce);
                authorizationRequest.setState(state);
                authorizationRequest.getPrompts().add(Prompt.NONE);
                authorizationRequest.setAuthUsername(userId);
                authorizationRequest.setAuthPassword(userSecret);

                JwtAuthorizationRequest jwtAuthorizationRequest = new JwtAuthorizationRequest(authorizationRequest, SignatureAlgorithm.HS256, clientSecret);
                jwtAuthorizationRequest.addIdTokenClaim(new Claim(JwtClaimName.SUBJECT_IDENTIFIER, ClaimValue.createSingleValue("INVALID_USER_ID")));
                String authJwt = jwtAuthorizationRequest.getEncodedJwt();
                authorizationRequest.setRequest(authJwt);
                System.out.println("Request JWT: " + authJwt);

                request.addHeader("Authorization", "Basic " + authorizationRequest.getEncodedCredentials());
                request.addHeader("Accept", MediaType.TEXT_PLAIN);
                request.setQueryString(authorizationRequest.getQueryString());
            }

            @Override
            protected void onResponse(EnhancedMockHttpServletResponse response) {
                super.onResponse(response);
                showResponse("requestParameterMethodFail4", response);

                assertEquals(response.getStatus(), 302, "Unexpected response code.");
                assertNotNull(response.getHeader("Location"), "Unexpected result: " + response.getHeader("Location"));

                if (response.getHeader("Location") != null) {
                    try {
                        URI uri = new URI(response.getHeader("Location").toString());
                        assertNotNull(uri.getFragment(), "Fragment is null");

                        Map<String, String> params = QueryStringDecoder.decode(uri.getFragment());

                        assertNotNull(params.get("error"), "The error value is null");
                        assertNotNull(params.get("error_description"), "The errorDescription value is null");
                        assertNotNull(params.get("state"), "The state is null");
                    } catch (URISyntaxException e) {
                        e.printStackTrace();
                        fail("Response URI is not well formed");
                    }
                }
            }
        }.run();
    }

    @Parameters({"authorizePath", "userId", "userSecret", "clientId", "clientSecret", "redirectUri"})
    @Test
    public void requestParameterMethodWithMaxAgeRestriction(final String authorizePath,
                                                            final String userId, final String userSecret,
                                                            final String clientId, final String clientSecret,
                                                            final String redirectUri) throws Exception {
        new ResourceRequestEnvironment.ResourceRequest(new ResourceRequestEnvironment(this), ResourceRequestEnvironment.Method.GET, authorizePath) {

            @Override
            protected void prepareRequest(EnhancedMockHttpServletRequest request) {
                super.prepareRequest(request);

                List<ResponseType> responseTypes = Arrays.asList(ResponseType.TOKEN, ResponseType.ID_TOKEN);
                List<String> scopes = Arrays.asList("openid", "profile", "address", "email");
                String nonce = UUID.randomUUID().toString();
                String state = "af0ifjsldkj";

                AuthorizationRequest authorizationRequest = new AuthorizationRequest(
                        responseTypes, clientId, scopes, redirectUri, nonce);
                authorizationRequest.setState(state);
                authorizationRequest.getPrompts().add(Prompt.NONE);
                authorizationRequest.setAuthUsername(userId);
                authorizationRequest.setAuthPassword(userSecret);

                JwtAuthorizationRequest jwtAuthorizationRequest = new JwtAuthorizationRequest(authorizationRequest, SignatureAlgorithm.HS256, clientSecret);
                jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.NAME, ClaimValue.createNull()));
                jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.NICKNAME, ClaimValue.createEssential(false)));
                jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.EMAIL, ClaimValue.createNull()));
                jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.EMAIL_VERIFIED, ClaimValue.createNull()));
                jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.PICTURE, ClaimValue.createEssential(false)));
                jwtAuthorizationRequest.addIdTokenClaim(new Claim(JwtClaimName.AUTHENTICATION_TIME, ClaimValue.createNull()));
                jwtAuthorizationRequest.addIdTokenClaim(new Claim(JwtClaimName.AUTHENTICATION_CONTEXT_CLASS_REFERENCE, ClaimValue.createValueList(new String[]{"2"})));
                jwtAuthorizationRequest.getIdTokenMember().setMaxAge(86400);
                String authJwt = jwtAuthorizationRequest.getEncodedJwt();
                authorizationRequest.setRequest(authJwt);
                System.out.println("Request JWT: " + authJwt);

                request.addHeader("Authorization", "Basic " + authorizationRequest.getEncodedCredentials());
                request.addHeader("Accept", MediaType.TEXT_PLAIN);
                request.setQueryString(authorizationRequest.getQueryString());
            }

            @Override
            protected void onResponse(EnhancedMockHttpServletResponse response) {
                super.onResponse(response);
                showResponse("requestParameterMethodWithMaxAgeRestriction", response);

                assertEquals(response.getStatus(), 302, "Unexpected response code.");
                assertNotNull(response.getHeader("Location"), "Unexpected result: " + response.getHeader("Location"));

                try {
                    URI uri = new URI(response.getHeader("Location").toString());
                    assertNotNull(uri.getQuery(), "Query string is null");

                    Map<String, String> params = QueryStringDecoder.decode(uri.getFragment());

                    assertNotNull(params.get("access_token"), "The accessToken is null");
                    assertNotNull(params.get("id_token"), "The idToken is null");
                    assertNotNull(params.get("scope"), "The scope is null");
                    assertNotNull(params.get("state"), "The state is null");
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                    fail("Response URI is not well formed");
                } catch (Exception e) {
                    e.printStackTrace();
                    fail("Response URI is not well formed");
                }
            }
        }.run();
    }

    @Parameters({"authorizePath", "userId", "userSecret", "clientId", "clientSecret", "redirectUri",
            "requestFileBasePath", "requestFileBaseUrl"})
    @Test // This tests requires a place to publish a request object via HTTPS
    public void requestFileMethod(final String authorizePath,
                                  final String userId, final String userSecret,
                                  final String clientId, final String clientSecret,
                                  final String redirectUri,
                                  final String requestFileBasePath, final String requestFileBaseUrl) throws Exception {
        new ResourceRequestEnvironment.ResourceRequest(new ResourceRequestEnvironment(this), ResourceRequestEnvironment.Method.GET, authorizePath) {

            @Override
            protected void prepareRequest(EnhancedMockHttpServletRequest request) {
                super.prepareRequest(request);

                List<ResponseType> responseTypes = Arrays.asList(ResponseType.TOKEN, ResponseType.ID_TOKEN);
                List<String> scopes = Arrays.asList("openid", "profile", "address", "email");
                String nonce = UUID.randomUUID().toString();
                String state = "af0ifjsldkj";

                AuthorizationRequest authorizationRequest = new AuthorizationRequest(
                        responseTypes, clientId, scopes, redirectUri, nonce);
                authorizationRequest.setState(state);
                authorizationRequest.getPrompts().add(Prompt.NONE);
                authorizationRequest.setAuthUsername(userId);
                authorizationRequest.setAuthPassword(userSecret);

                try {
                    JwtAuthorizationRequest jwtAuthorizationRequest = new JwtAuthorizationRequest(authorizationRequest, SignatureAlgorithm.HS256, clientSecret);
                    jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.NAME, ClaimValue.createNull()));
                    jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.NICKNAME, ClaimValue.createEssential(false)));
                    jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.EMAIL, ClaimValue.createNull()));
                    jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.EMAIL_VERIFIED, ClaimValue.createNull()));
                    jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.PICTURE, ClaimValue.createEssential(false)));
                    jwtAuthorizationRequest.addIdTokenClaim(new Claim(JwtClaimName.AUTHENTICATION_TIME, ClaimValue.createNull()));
                    jwtAuthorizationRequest.addIdTokenClaim(new Claim(JwtClaimName.AUTHENTICATION_CONTEXT_CLASS_REFERENCE, ClaimValue.createValueList(new String[]{"2"})));
                    jwtAuthorizationRequest.getIdTokenMember().setMaxAge(86400);
                    String authJwt = jwtAuthorizationRequest.getEncodedJwt();
                    String hash = JwtUtil.base64urlencode(JwtUtil.getMessageDigestSHA256(authJwt));
                    String fileName = UUID.randomUUID().toString() + ".txt";
                    String filePath = requestFileBasePath + File.separator + fileName;
                    String fileUrl = requestFileBaseUrl + "/" + fileName + "#" + hash;
                    FileWriter fw = new FileWriter(filePath);
                    BufferedWriter bw = new BufferedWriter(fw);
                    bw.write(authJwt);
                    bw.close();
                    fw.close();
                    authorizationRequest.setRequestUri(fileUrl);
                    System.out.println("Request JWT: " + authJwt);
                    System.out.println("Request File Path: " + filePath);
                    System.out.println("Request File URL: " + fileUrl);
                } catch (IOException e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                } catch (NoSuchProviderException e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                }

                request.addHeader("Authorization", "Basic " + authorizationRequest.getEncodedCredentials());
                request.addHeader("Accept", MediaType.TEXT_PLAIN);
                request.setQueryString(authorizationRequest.getQueryString());
            }

            @Override
            protected void onResponse(EnhancedMockHttpServletResponse response) {
                super.onResponse(response);
                showResponse("requestFileMethod", response);

                assertEquals(response.getStatus(), 302, "Unexpected response code.");
                assertNotNull(response.getHeader("Location"), "Unexpected result: " + response.getHeader("Location"));

                try {
                    URI uri = new URI(response.getHeader("Location").toString());
                    assertNotNull(uri.getQuery(), "Query string is null");

                    Map<String, String> params = QueryStringDecoder.decode(uri.getFragment());

                    assertNotNull(params.get("access_token"), "The accessToken is null");
                    assertNotNull(params.get("id_token"), "The idToken is null");
                    assertNotNull(params.get("scope"), "The scope is null");
                    assertNotNull(params.get("state"), "The state is null");
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                    fail("Response URI is not well formed");
                } catch (Exception e) {
                    e.printStackTrace();
                    fail("Response URI is not well formed");
                }
            }
        }.run();
    }

    @Parameters({"authorizePath", "userId", "userSecret", "clientId", "redirectUri"})
    @Test
    public void requestFileMethodFail1(final String authorizePath,
                                       final String userId, final String userSecret,
                                       final String clientId, final String redirectUri) throws Exception {
        new ResourceRequestEnvironment.ResourceRequest(new ResourceRequestEnvironment(this), ResourceRequestEnvironment.Method.GET, authorizePath) {

            @Override
            protected void prepareRequest(EnhancedMockHttpServletRequest request) {
                super.prepareRequest(request);

                List<ResponseType> responseTypes = Arrays.asList(ResponseType.TOKEN, ResponseType.ID_TOKEN);
                List<String> scopes = Arrays.asList("openid", "profile", "address", "email");
                String nonce = UUID.randomUUID().toString();
                String state = "af0ifjsldkj";

                AuthorizationRequest authorizationRequest = new AuthorizationRequest(
                        responseTypes, clientId, scopes, redirectUri, nonce);
                authorizationRequest.setState(state);
                authorizationRequest.getPrompts().add(Prompt.NONE);
                authorizationRequest.setAuthUsername(userId);
                authorizationRequest.setAuthPassword(userSecret);

                authorizationRequest.setRequest("FAKE_REQUEST");
                authorizationRequest.setRequestUri("FAKE_REQUEST_URI");

                request.addHeader("Authorization", "Basic " + authorizationRequest.getEncodedCredentials());
                request.addHeader("Accept", MediaType.TEXT_PLAIN);
                request.setQueryString(authorizationRequest.getQueryString());
            }

            @Override
            protected void onResponse(EnhancedMockHttpServletResponse response) {
                super.onResponse(response);
                showResponse("requestFileMethodFail1", response);

                assertEquals(response.getStatus(), 302, "Unexpected response code.");
                assertNotNull(response.getHeader("Location"), "Unexpected result: " + response.getHeader("Location"));

                if (response.getHeader("Location") != null) {
                    try {
                        URI uri = new URI(response.getHeader("Location").toString());
                        assertNotNull(uri.getFragment(), "Fragment is null");

                        Map<String, String> params = QueryStringDecoder.decode(uri.getFragment());

                        assertNotNull(params.get("error"), "The error value is null");
                        assertNotNull(params.get("error_description"), "The errorDescription value is null");
                        assertNotNull(params.get("state"), "The state is null");
                    } catch (URISyntaxException e) {
                        e.printStackTrace();
                        fail("Response URI is not well formed");
                    }
                }
            }
        }.run();
    }

    @Parameters({"authorizePath", "userId", "userSecret", "clientId", "redirectUri", "requestFileBaseUrl"})
    @Test
    public void requestFileMethodFail2(final String authorizePath,
                                       final String userId, final String userSecret,
                                       final String clientId, final String redirectUri,
                                       final String requestFileBaseUrl) throws Exception {
        new ResourceRequestEnvironment.ResourceRequest(new ResourceRequestEnvironment(this), ResourceRequestEnvironment.Method.GET, authorizePath) {

            @Override
            protected void prepareRequest(EnhancedMockHttpServletRequest request) {
                super.prepareRequest(request);

                List<ResponseType> responseTypes = Arrays.asList(ResponseType.TOKEN, ResponseType.ID_TOKEN);
                List<String> scopes = Arrays.asList("openid", "profile", "address", "email");
                String nonce = UUID.randomUUID().toString();
                String state = "af0ifjsldkj";

                AuthorizationRequest authorizationRequest = new AuthorizationRequest(
                        responseTypes, clientId, scopes, redirectUri, nonce);
                authorizationRequest.setState(state);
                authorizationRequest.getPrompts().add(Prompt.NONE);
                authorizationRequest.setAuthUsername(userId);
                authorizationRequest.setAuthPassword(userSecret);

                authorizationRequest.setRequestUri(requestFileBaseUrl + "/FAKE_REQUEST_URI");

                request.addHeader("Authorization", "Basic " + authorizationRequest.getEncodedCredentials());
                request.addHeader("Accept", MediaType.TEXT_PLAIN);
                request.setQueryString(authorizationRequest.getQueryString());
            }

            @Override
            protected void onResponse(EnhancedMockHttpServletResponse response) {
                super.onResponse(response);
                showResponse("requestFileMethodFail2", response);

                assertEquals(response.getStatus(), 302, "Unexpected response code.");
                assertNotNull(response.getHeader("Location"), "Unexpected result: " + response.getHeader("Location"));

                if (response.getHeader("Location") != null) {
                    try {
                        URI uri = new URI(response.getHeader("Location").toString());
                        assertNotNull(uri.getFragment(), "Fragment is null");

                        Map<String, String> params = QueryStringDecoder.decode(uri.getFragment());

                        assertNotNull(params.get("error"), "The error value is null");
                        assertNotNull(params.get("error_description"), "The errorDescription value is null");
                        assertNotNull(params.get("state"), "The state is null");
                    } catch (URISyntaxException e) {
                        e.printStackTrace();
                        fail("Response URI is not well formed");
                    }
                }
            }
        }.run();
    }

    @Parameters({"authorizePath", "userId", "userSecret", "clientId", "clientSecret", "redirectUri",
            "requestFileBasePath", "requestFileBaseUrl"})
    @Test // This test requires a place to publish a request object via HTTPS
    public void requestFileMethodFail3(final String authorizePath,
                                       final String userId, final String userSecret,
                                       final String clientId, final String clientSecret,
                                       final String redirectUri,
                                       final String requestFileBasePath, final String requestFileBaseUrl) throws Exception {
        new ResourceRequestEnvironment.ResourceRequest(new ResourceRequestEnvironment(this), ResourceRequestEnvironment.Method.GET, authorizePath) {

            @Override
            protected void prepareRequest(EnhancedMockHttpServletRequest request) {
                super.prepareRequest(request);

                List<ResponseType> responseTypes = Arrays.asList(ResponseType.TOKEN, ResponseType.ID_TOKEN);
                List<String> scopes = Arrays.asList("openid", "profile", "address", "email");
                String nonce = UUID.randomUUID().toString();
                String state = "af0ifjsldkj";

                AuthorizationRequest authorizationRequest = new AuthorizationRequest(
                        responseTypes, clientId, scopes, redirectUri, nonce);
                authorizationRequest.setState(state);
                authorizationRequest.getPrompts().add(Prompt.NONE);
                authorizationRequest.setAuthUsername(userId);
                authorizationRequest.setAuthPassword(userSecret);

                try {
                    JwtAuthorizationRequest jwtAuthorizationRequest = new JwtAuthorizationRequest(authorizationRequest, SignatureAlgorithm.HS256, clientSecret);
                    jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.NAME, ClaimValue.createNull()));
                    jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.NICKNAME, ClaimValue.createEssential(false)));
                    jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.EMAIL, ClaimValue.createNull()));
                    jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.EMAIL_VERIFIED, ClaimValue.createNull()));
                    jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.PICTURE, ClaimValue.createEssential(false)));
                    jwtAuthorizationRequest.addIdTokenClaim(new Claim(JwtClaimName.AUTHENTICATION_TIME, ClaimValue.createNull()));
                    jwtAuthorizationRequest.addIdTokenClaim(new Claim(JwtClaimName.AUTHENTICATION_CONTEXT_CLASS_REFERENCE, ClaimValue.createValueList(new String[]{"2"})));
                    jwtAuthorizationRequest.getIdTokenMember().setMaxAge(86400);
                    String authJwt = jwtAuthorizationRequest.getEncodedJwt();
                    String hash = "INVALID_HASH";
                    String fileName = UUID.randomUUID().toString() + ".txt";
                    String filePath = requestFileBasePath + File.separator + fileName;
                    String fileUrl = requestFileBaseUrl + "/" + fileName + "#" + hash;
                    FileWriter fw = new FileWriter(filePath);
                    BufferedWriter bw = new BufferedWriter(fw);
                    bw.write(authJwt);
                    bw.close();
                    fw.close();
                    authorizationRequest.setRequestUri(fileUrl);
                    System.out.println("Request JWT: " + authJwt);
                    System.out.println("Request File Path: " + filePath);
                    System.out.println("Request File URL: " + fileUrl);
                } catch (IOException e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                }

                request.addHeader("Authorization", "Basic " + authorizationRequest.getEncodedCredentials());
                request.addHeader("Accept", MediaType.TEXT_PLAIN);
                request.setQueryString(authorizationRequest.getQueryString());
            }

            @Override
            protected void onResponse(EnhancedMockHttpServletResponse response) {
                super.onResponse(response);
                showResponse("requestFileMethodFail3", response);

                assertEquals(response.getStatus(), 302, "Unexpected response code.");
                assertNotNull(response.getHeader("Location"), "Unexpected result: " + response.getHeader("Location"));

                if (response.getHeader("Location") != null) {
                    try {
                        URI uri = new URI(response.getHeader("Location").toString());
                        assertNotNull(uri.getFragment(), "Fragment is null");

                        Map<String, String> params = QueryStringDecoder.decode(uri.getFragment());

                        assertNotNull(params.get("error"), "The error value is null");
                        assertNotNull(params.get("error_description"), "The errorDescription value is null");
                        assertNotNull(params.get("state"), "The state is null");
                    } catch (URISyntaxException e) {
                        e.printStackTrace();
                        fail("Response URI is not well formed");
                    }
                }
            }
        }.run();
    }

    @Parameters({"registerPath", "redirectUris"})
    @Test
    public void requestParameterMethodAlgNoneStep1(final String registerPath, final String redirectUris) throws Exception {
        new ResourceRequestEnvironment.ResourceRequest(new ResourceRequestEnvironment(this),
                ResourceRequestEnvironment.Method.POST, registerPath) {

            @Override
            protected void prepareRequest(EnhancedMockHttpServletRequest request) {
                try {
                    super.prepareRequest(request);

                    List<ResponseType> responseTypes = Arrays.asList(ResponseType.TOKEN);

                    RegisterRequest registerRequest = new RegisterRequest(ApplicationType.WEB, "oxAuth test app",
                            StringUtils.spaceSeparatedToList(redirectUris));
                    registerRequest.setResponseTypes(responseTypes);
                    registerRequest.setRequestObjectSigningAlg(SignatureAlgorithm.NONE);
                    registerRequest.addCustomAttribute("oxAuthTrustedClient", "true");

                    request.setContentType(MediaType.APPLICATION_JSON);
                    String registerRequestContent = registerRequest.getJSONParameters().toString(4);
                    request.setContent(registerRequestContent.getBytes());
                } catch (JSONException e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                }
            }

            @Override
            protected void onResponse(EnhancedMockHttpServletResponse response) {
                super.onResponse(response);
                showResponse("requestParameterMethodAlgNoneStep1", response);

                assertEquals(response.getStatus(), 200, "Unexpected response code. " + response.getContentAsString());
                assertNotNull(response.getContentAsString(), "Unexpected result: " + response.getContentAsString());
                try {
                    JSONObject jsonObj = new JSONObject(response.getContentAsString());
                    assertTrue(jsonObj.has(RegisterResponseParam.CLIENT_ID.toString()));
                    assertTrue(jsonObj.has(CLIENT_SECRET.toString()));
                    assertTrue(jsonObj.has(REGISTRATION_ACCESS_TOKEN.toString()));
                    assertTrue(jsonObj.has(REGISTRATION_CLIENT_URI.toString()));
                    assertTrue(jsonObj.has(CLIENT_ID_ISSUED_AT.toString()));
                    assertTrue(jsonObj.has(CLIENT_SECRET_EXPIRES_AT.toString()));

                    clientId3 = jsonObj.getString(RegisterResponseParam.CLIENT_ID.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                    fail(e.getMessage() + "\nResponse was: " + response.getContentAsString());
                }
            }
        }.run();
    }

    @Parameters({"authorizePath", "userId", "userSecret", "redirectUri"})
    @Test(dependsOnMethods = "requestParameterMethodAlgNoneStep1")
    public void requestParameterMethodAlgNoneStep2(
            final String authorizePath, final String userId, final String userSecret, final String redirectUri) throws Exception {
        new ResourceRequestEnvironment.ResourceRequest(new ResourceRequestEnvironment(this), ResourceRequestEnvironment.Method.GET, authorizePath) {

            @Override
            protected void prepareRequest(EnhancedMockHttpServletRequest request) {
                super.prepareRequest(request);

                List<ResponseType> responseTypes = Arrays.asList(ResponseType.TOKEN);
                List<String> scopes = Arrays.asList("openid");
                String nonce = UUID.randomUUID().toString();
                String state = "af0ifjsldkj";

                AuthorizationRequest authorizationRequest = new AuthorizationRequest(
                        responseTypes, clientId3, scopes, redirectUri, nonce);
                authorizationRequest.setState(state);
                authorizationRequest.getPrompts().add(Prompt.NONE);
                authorizationRequest.setAuthUsername(userId);
                authorizationRequest.setAuthPassword(userSecret);

                JwtAuthorizationRequest jwtAuthorizationRequest = new JwtAuthorizationRequest(authorizationRequest);
                jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.NAME, ClaimValue.createNull()));
                jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.NICKNAME, ClaimValue.createEssential(false)));
                jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.EMAIL, ClaimValue.createNull()));
                jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.EMAIL_VERIFIED, ClaimValue.createNull()));
                jwtAuthorizationRequest.addUserInfoClaim(new Claim(JwtClaimName.PICTURE, ClaimValue.createEssential(false)));
                jwtAuthorizationRequest.addIdTokenClaim(new Claim(JwtClaimName.AUTHENTICATION_TIME, ClaimValue.createNull()));
                jwtAuthorizationRequest.addIdTokenClaim(new Claim(JwtClaimName.AUTHENTICATION_CONTEXT_CLASS_REFERENCE, ClaimValue.createValueList(new String[]{"2"})));
                String authJwt = jwtAuthorizationRequest.getEncodedJwt();
                authorizationRequest.setRequest(authJwt);
                System.out.println("Request JWT: " + authJwt);

                request.addHeader("Authorization", "Basic " + authorizationRequest.getEncodedCredentials());
                request.addHeader("Accept", MediaType.TEXT_PLAIN);
                request.setQueryString(authorizationRequest.getQueryString());
            }

            @Override
            protected void onResponse(EnhancedMockHttpServletResponse response) {
                super.onResponse(response);
                showResponse("requestParameterMethodAlgNoneStep2", response);

                assertEquals(response.getStatus(), 302, "Unexpected response code.");
                assertNotNull(response.getHeader("Location"), "Unexpected result: " + response.getHeader("Location"));

                try {
                    URI uri = new URI(response.getHeader("Location").toString());
                    assertNotNull(uri.getQuery(), "Query string is null");

                    Map<String, String> params = QueryStringDecoder.decode(uri.getFragment());

                    assertNotNull(params.get("access_token"), "The accessToken is null");
                    assertNotNull(params.get("scope"), "The scope is null");
                    assertNotNull(params.get("state"), "The state is null");
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                    fail("Response URI is not well formed");
                }
            }
        }.run();
    }
}
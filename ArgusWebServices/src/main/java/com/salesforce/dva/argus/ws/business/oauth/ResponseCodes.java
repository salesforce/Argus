package com.salesforce.dva.argus.ws.business.oauth;

public final class ResponseCodes {
    public static final String CANNOT_REGISTER_APP = "{\"error\": \"cannot issue client_id and client_secret\"}";
    public static final String NAME_OR_SCOPE_OR_URI_IS_NULL = "{\"error\": \"name, scope or redirect_uri is missing or invalid\"}";
    public static final String SCOPE_NOT_EXIST = "{\"error\": \"scope does not exist\"}";
    public static final String INVALID_CLIENT_ID = "{\"error\": \"invalid client_id/client_secret\"}";
    public static final String INVALID_CLIENT_CREDENTIALS = "{\"error\": \"invalid client_id/client_secret\"}";
    public static final String RESPONSE_TYPE_NOT_SUPPORTED = "{\"error\": \"unsupported_response_type\"}";
    public static final String INVALID_REDIRECT_URI = "{\"error\": \"invalid redirect_uri\"}";
    public static final String INVALID_GRANT_TYPE = "{\"error\": \"invalid grant_type\"}";
    public static final String MANDATORY_PARAM_MISSING = "{\"error\": \"mandatory parameter %s is missing\"}";
    public static final String CANNOT_ISSUE_TOKEN = "{\"error\": \"cannot issue token\"}";
    public static final String NOT_ABLE_TO_ISSUE_AUTH_CODE = "{\"error\": \"not able to issue authorization code, internal error\"}";
    public static final String NOT_ABLE_TO_ISSUE_ACCESS_TOKEN = "{\"error\": \"not able to issue access token, internal error\"}";
    public static final String INVALID_AUTH_CODE = "{\"error\": \"invalid auth_code\"}";
    public static final String INVALID_STATE = "{\"error\": \"invalid state\"}";
    public static final String GRANT_TYPE_NOT_SUPPORTED = "{\"error\": \"unsupported_grant_type\"}";
    public static final String INVALID_ACCESS_TOKEN = "{\"error\":\"invalid access token\"}";
    public static final String INVALID_REFRESH_TOKEN = "{\"error\":\"invalid refresh token\"}";
    public static final String INVALID_USERNAME_PASSWORD = "{\"error\": \"invalid username/password\"}";
    public static final String CANNOT_AUTHENTICATE_USER = "{\"error\": \"cannot authenticate user\"}";
    public static final String NOT_FOUND_CONTENT = "{\"error\":\"Not found\"}";
    public static final String UNSUPPORTED_MEDIA_TYPE = "{\"error\":\"unsupported media type\"}";
    public static final String CANNOT_UPDATE_APP = "{\"error\": \"cannot update client application\"}";
    public static final String UPDATE_APP_MANDATORY_PARAM_MISSING = "{\"error\": \"scope, description or status is missing or invalid\"}";
    public static final String ALREADY_REGISTERED_APP = "{\"error\": \"already registered client application\"}";
    public static final String CLIENT_APP_NOT_EXIST = "{\"error\": \"client application does not exist\"}";
    public static final String SCOPE_NOK_MESSAGE = "{\"status\":\"scope not valid\"}";
    public static final String CLIENT_APP_UPDATED = "{\"status\":\"client application updated\"}";
    public static final String CANNOT_LIST_CLIENT_APPS = "{\"error\":\"cannot list client applications\"}";
    public static final String INVALID_JSON_ERROR = "{\"error\":\"invalid JSON\"}";
    public static final String ERROR_NOT_INTEGER = "{\"error\":\"%s is not an integer\"}";

    public static final String APPLICATION_JSON = "application/json";
}

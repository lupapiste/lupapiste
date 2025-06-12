# OAuth 2 in Lupapiste

Lupapiste provides OAuth 2 authorization endpoints. These can be used to gain authorization from a user to access
their user data and, when applicable, bill the corporate account they are associated with.

Lupapiste supports _authorization code_ and _implicit_ grant types.

## Registering an application

The OAuth authorization endpoints must be accessed with valid client credentials. These may be created by adding
an object resembling the following under the key `oauth` in a user document:

```json
{
  "client-id" : "docstore",
  "client-secret" : "docstore",
  "scopes" : [ "read", "pay" ],
  "display-name" : { "fi" : "Lupapiste kauppa", "sv" : "Lupapiste butik", "en" : "Lupapiste store" },
  "callback-url" : "http://localhost:8000"
}
```

Client id and client secret will be used to identify the client application and the display name will be shown on the
authorization UI. Callback-url is the URL part consisting of protocol and host, which the provided success and error
callback paths will be appended to, forming the full success and failure URLs that will be called with the
authorization code / token or error message.

Currently supported scopes are `read` for retrieving user data and `pay` for asking permission to bill the corporate
account the user is associated with.

## Authorization code grant type

This is the more secure flow and should be used by server side applications.

The flow consists of these steps:

1. Redirect the user to:
   `https://www.lupapiste.fi/oauth/authorize?lang=fi&scope=read,pay&client_id=<client_id>&response_type=code&
   success_callback=/success&error_callback=/error`.
   The query parameters are required. `lang` may be `fi`, `en` or `sv`, `scope` is a comma-separated list of the scopes
   you are asking authorization for. `client_id` is the client id stored in Lupapiste db and `response_type`is `code`.
   `success_callback` is the path on your server the user is redirected on success, and likewise `error_callback` is
   where the user is redirected on error. The callback paths must begin with `/`.
2. The user is redirected to Lupapiste login screen if they do not have an active session.
3. Authorization UI is presented to the user and they may approve or decline the authorization.
4. If the user approves, the success callback URL will be called with the query parameter `?code=<authorization code>`,
   e.g. `http://somesite.fi/success?code=absc325236`.
5. The client application may now exchange the authorization code for an access token by POSTing to:<br>
  `https://www.lupapiste.fi/oauth/token?client_id=<client-id>&client_secret=<client-secret>&grant_type=authorization_code&code=<authorization code>`.
   The response will be a JSON object similar to the following:
```json
{
    "access_token": "kvn8P596LUM4GX2uk7uLn195LH0a5VHkmiW80m4oHoyAlAwZ",
    "refresh_token": "crU6bDgGL1uUtMQxrJrul8U7rI1i81Z8l24qyPCba9yFOCvj",
    "token_type": "bearer",
    "expires_in": 600000,
    "scope": "read"
}
```
6. The access token may now be used to access service APIs.
7. The refresh token has a longer expiration than the access token and
   it can be used to get new access (and refresh) tokens. POST to the
   same token endpoint with the same `client_id` and `client_secret`
   params, but the `grant_type` must be `refresh_token`. The response
   will have new access and refresh tokens. The scope remains the same
   and is not included in the response. Note that the refresh token
   can only be used once.

**Note that the expiry time of the authorization code is 1 minute and it can only be used once.**

## Implicit grant type

This flow may be used by front-end client applications (e.g. Javascript or mobile apps) that cannot keep the
client-secret secure.

1. Redirect the user to:
   `https://www.lupapiste.fi/oauth/authorize?lang=fi&scope=read,pay&client_id=<client_id>&response_type=token&
   success_callback=/success&error_callback=/error`.
   The query parameters are required. `lang` may be `fi`, `en` or `sv`, `scope` is a comma-separated list of the scopes
   you are asking authorization for. `client_id` is the client id stored in Lupapiste db and `response_type`is `token`.
   `success_callback` is the path on your server the user is redirected on success, and likewise `error_callback` is
   where the user is redirected on error. The callback paths must begin with `/`.
2. The user is redirected to Lupapiste login screen if they do not have an active session.
3. Authorization UI is presented to the user and they may approve or decline the authorization.
4. If the user approves, the success callback URL will be called with the URL hash / fragment `#token=<access token>`,
   e.g. `http://somesite.fi/success#token=absc325236`.
5. The access token may now be used to access service APIs.

## Error situations

The user may decide to cancel the authorization. In this case the failure url of the application will be called with
`?error=authorization_cancelled`, e.g. `http://somesite.fi/failure?error=authorization_cancelled`.

If the client application asks for a `pay` scope and the user does not have the ability to pay (e.g. she's not
associated with a corporate account), the user agent will be immediately redirected to the failure url callback with
the parameter `?error=cannot_pay`, e.g. `http://somesite.fi/failure?error=cannot_pay`.

## Service APIs available with the access token

The client application may use the access token to authenticate by setting the `Authorization` header to the value
`Bearer <access token>`, e.g. `Bearer gfasf2435325`.

The only service API currently accessible with the access token is the one for retrieving the user details.

### GET /rest/user

This endpoint takes no parameters. The response is a JSON object with the user details.

Corporate user example:
```json
{
    "role": "applicant",
    "email": "kaino@solita.fi",
    "firstName": "Kaino",
    "lastName": "Solita",
    "company": {
        "id": "solita",
        "name": "Solita Oy"
    }
}
```

Authority user example:
```json
{
    "role": "authority",
    "email": "sonja.sibbo@sipoo.fi",
    "firstName": "Sonja",
    "lastName": "Sibbo",
    "organizations": [
        {"id": "753-R",
         "roles": ["authority", "approver"]}
    ]
}
```

The `company` key and object will be present only if the user is associated with a corporate account.
The `organizatioons` key and array will be present only if the user is a member of an organization.

User `role` may be `applicant`, `authority`, `authorityAdmin` or `admin`. The role `admin` is an administrator of the
Lupapiste service and related ecosystem.

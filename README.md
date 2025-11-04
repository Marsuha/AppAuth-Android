# AppAuth for Android

[![Build Status](https://github.com/openid/AppAuth-Android/workflows/Android%20CI/badge.svg)](https://github.com/openid/AppAuth-Android/actions)
[![Maven Central](https://img.shields.io/maven-central/v/net.openid/appauth.svg?label=maven-central)](https://search.maven.org/search?q=g:net.openid%20a:appauth)

AppAuth for Android is a client SDK for communicating with OAuth 2.0 and
OpenID Connect providers. It strives to directly map the requests and responses
of those specifications, while following the idiomatic style of the implementation
language. In addition to mapping the raw protocol flows, convenience methods are
available to assist with common tasks like performing an action with fresh tokens.

It follows the best practices set out in [RFC 8252 - OAuth 2.0 for Native
Apps](https://tools.ietf.org/html/rfc8252) including using
[Custom Tabs](https://developer.chrome.com/multidevice/android/customtabs)
for the auth request. The library is also supported for
[Enterprise use on ChromeOS](https://developer.android.com/topic/arc/sso-for-enterprise-in-chromeos).
A talk by one of the authors of AppAuth (at Google, for Google employees, but
made public) can be found here:
[Enterprise SSO with Chrome Custom Tabs](https://www.youtube.com/watch?v=DdQTXrk6YTk).

## Download

AppAuth for Android is available on [MavenCentral](https://search.maven.org/search?q=g:net.openid%20appauth)

```groovy
implementation 'net.openid:appauth:<version>'
```

## Requirements

AppAuth supports Android API 16 (Jellybean) and above. Browsers which provide a custom tabs
implementation are preferred by the library, but not required.
Both Custom URI Schemes (all supported versions of Android) and App Links (Android M / API 23+) can
be used with the library.

In general, AppAuth can work with any Authorization Server (AS) that supports
native apps as documented in [RFC 8252](https://tools.ietf.org/html/rfc8252),
either through custom URI scheme redirects, or App Links.
AS's that assume all clients are web-based or require clients to maintain
confidentiality of the client secrets may not work well.

## Demo app

A demo app is contained within this repository. For instructions on how to
build and configure this app, see the
[demo app readme](https://github.com/openid/AppAuth-Android/blob/master/app/README.md).

## Conceptual overview

AppAuth encapsulates the authorization state of the user in the
[net.openid.appauth.AuthState](https://github.com/openid/AppAuth-Android/blob/master/library/java/net/openid/appauth/AuthState.java)
class, and communicates with an authorization server through the use of the
[net.openid.appauth.AuthorizationService](https://github.com/openid/AppAuth-Android/blob/master/library/java/net/openid/appauth/AuthorizationService.java)
class. AuthState is designed to be easily persistable as a JSON string, using
the storage mechanism of your choice (e.g.
[SharedPreferences](https://developer.android.com/training/basics/data-storage/shared-preferences.html),
[sqlite](https://developer.android.com/training/basics/data-storage/databases.html),
or even just
[in a file](https://developer.android.com/training/basics/data-storage/files.html)).

AppAuth provides data classes which are intended to model the OAuth2
specification as closely as possible; this provides the greatest flexibility
in interacting with a wide variety of OAuth2 and OpenID Connect implementations.

Authorizing the user occurs via the user's web browser, and the request
is described using instances of
[AuthorizationRequest](https://github.com/openid/AppAuth-Android/blob/master/library/java/net/openid/appauth/AuthorizationRequest.java).
The request is dispatched using
`performAuthorizationRequest()` on an AuthorizationService instance, and the response (an
[AuthorizationResponse](https://github.com/openid/AppAuth-Android/blob/master/library/java/net/openid/appauth/AuthorizationResponse.java) instance) will be dispatched to the activity of your choice,
expressed via an Intent.

Token requests, such as obtaining a new access token using a refresh token,
follow a similar pattern:
[TokenRequest](https://github.com/openid/AppAuth-Android/blob/master/library/java/net/openid/appauth/TokenRequest.java) instances are dispatched using
`performTokenRequest()` on an AuthorizationService instance, and a
[TokenResponse](https://github.com/openid/AppAuth-Android/blob/master/library/java/net/openid/appauth/TokenResponse.java)
instance is returned via a callback.

Responses can be provided to the
`update()`
methods on AuthState in order to track and persist changes to the authorization
state. Once in an authorized state, the
`performActionWithFreshTokens()`
method on AuthState can be used to automatically refresh access tokens
as necessary before performing actions that require valid tokens.

## Implementing the authorization code flow

It is recommended that native apps use the
[authorization code](https://tools.ietf.org/html/rfc6749#section-1.3.1)
flow with a public client to gain authorization to access user data. This has
the primary advantage for native clients that the authorization flow, which
must occur in a browser, only needs to be performed once.

This flow is effectively composed of four stages:

1. Discovering or specifying the endpoints to interact with the provider.
2. Authorizing the user, via a browser, in order to obtain an authorization
   code.
3. Exchanging the authorization code with the authorization server, to obtain
   a refresh token and/or ID token.
4. Using access tokens derived from the refresh token to interact with a
   resource server for further access to user data.

At each step of the process, an AuthState instance can (optionally) be updated
with the result to help with tracking the state of the flow.

### Authorization service configuration

First, AppAuth must be instructed how to interact with the authorization
service. This can be done either by directly creating an
[AuthorizationServiceConfiguration](https://github.com/openid/AppAuth-Android/blob/master/library/java/net/openid/appauth/AuthorizationServiceConfiguration.java#L102)
instance, or by retrieving an OpenID Connect discovery document.

Directly specifying an AuthorizationServiceConfiguration involves
providing the URIs of the authorization endpoint and token endpoint,
and optionally a dynamic client registration endpoint (see "Dynamic client
registration" for more info):

```kotlin
val serviceConfig = AuthorizationServiceConfiguration(
    Uri.parse("https://idp.example.com/auth"), // authorization endpoint
    Uri.parse("https://idp.example.com/token")  // token endpoint
)
```

Where available, using an OpenID Connect discovery document is preferable:

```kotlin
AuthorizationServiceConfiguration.fetchFromIssuer(
    Uri.parse("https://idp.example.com")
) { serviceConfiguration, ex ->
    if (ex != null) {
        Log.e(TAG, "failed to fetch configuration")
        return@fetchFromIssuer
    }

    // use serviceConfiguration as needed
}
```

This will attempt to download a discovery document from the standard location
under this base URI,
`https://idp.example.com/.well-known/openid-configuration`. If the discovery
document for your IDP is in some other non-standard location, you can instead
provide the full URI as follows:

```kotlin
AuthorizationServiceConfiguration.fetchFromUrl(
    Uri.parse("https://idp.example.com/exampletenant/openid-config")
) { serviceConfiguration, ex ->
    // ...
}
```

If desired, this configuration can be used to seed an AuthState instance,
to persist the configuration easily:

```kotlin
val authState = AuthState(serviceConfig)
```

### Obtaining an authorization code

An authorization code can now be acquired by constructing an
AuthorizationRequest, using its Builder. In AppAuth, the builders for each
data class accept the mandatory parameters via the builder constructor:

```kotlin
val authRequestBuilder = AuthorizationRequest.Builder(
    serviceConfig, // the authorization service configuration
    MY_CLIENT_ID, // the client ID, typically pre-registered and static
    ResponseTypeValues.CODE, // the response_type value: we want a code
    MY_REDIRECT_URI // the redirect URI to which the auth response is sent
)
```

Other optional parameters, such as the OAuth2
[scope string](https://tools.ietf.org/html/rfc6749#section-3.3)
or
OpenID Connect
[login hint](http://openid.net/specs/openid-connect-core-1_0.html#rfc.section.3.1.2.1)
are specified through set methods on the builder:

```kotlin
val authRequest = authRequestBuilder
    .setScope("openid email profile https://idp.example.com/custom-scope")
    .setLoginHint("jdoe@user.example.com")
    .build()
```

This request can then be dispatched using one of two approaches.

The recommended approach is to use Android's
[`ActivityResultLauncher`](https://developer.android.com/training/basics/intents/result),
which is the modern replacement for `startActivityForResult`. First, register a
launcher in your Activity or Fragment:

```kotlin
val authorizationLauncher = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) { result ->
    val data = result.data
    if (result.resultCode == Activity.RESULT_OK && data != null) {
        val response = AuthorizationResponse.fromIntent(data)
        val ex = AuthorizationException.fromIntent(data)
        // process the response or exception
        if (response != null) {
            // authorization succeeded
            updateAuthState(response, ex)
        }
    } else {
        // Authorization flow failed or was canceled by the user
    }
}
```

Then, create an `AuthorizationService` and use it to launch the authorization intent:

```kotlin
fun doAuthorization() {
    val authService = AuthorizationService(this)
    val authIntent = authService.getAuthorizationRequestIntent(authRequest)
    authorizationLauncher.launch(authIntent)
}
```

If instead you wish to directly transition to another activity on completion
or cancellation, you can use `performAuthorizationRequest` with `PendingIntent`s:

```kotlin
val authService = AuthorizationService(this)

val completionIntent = Intent(this, MyAuthCompleteActivity::class.java)
val cancelIntent = Intent(this, MyAuthCanceledActivity::class.java)

val completionPendingIntent = PendingIntent.getActivity(this, 0, completionIntent, PendingIntent.FLAG_IMMUTABLE)
val cancelPendingIntent = PendingIntent.getActivity(this, 0, cancelIntent, PendingIntent.FLAG_IMMUTABLE)


authService.performAuthorizationRequest(
    authRequest,
    completionPendingIntent,
    cancelPendingIntent
)
```

The intents may be customized to carry any additional data or flags required
for the correct handling of the authorization response.

#### Capturing the authorization redirect

Once the authorization flow is completed in the browser, the authorization
service will redirect to a URI specified as part of the authorization request,
providing the response via query parameters. In order for your app to
capture this response, it must register with the Android OS as a handler for
this redirect URI.

We recommend using a custom scheme based redirect URI (i.e. those of form
`my.scheme:/path`), as this is the most widely supported across all versions of
Android. To avoid conflicts with other apps, it is recommended to configure a
distinct scheme using "reverse domain name notation". This can either match
your service web domain (in reverse) e.g. `com.example.service` or your package
name `com.example.app` or be something completely new as long as it's distinct
enough. Using the package name of your app is quite common but it's not always
possible if it contains illegal characters for URI schemes (like underscores)
or if you already have another handler for that scheme - so just use something
else.

When a custom scheme is used, AppAuth can be easily configured to capture
all redirects using this custom scheme through a manifest placeholder in your
`build.gradle` file:

```groovy
android.defaultConfig.manifestPlaceholders = [
  'appAuthRedirectScheme': 'com.example.app'
]
```

Alternatively, the redirect URI can be directly configured by adding an
intent-filter for AppAuth's RedirectUriReceiverActivity to your
AndroidManifest.xml:

```xml
<activity
        android:name="net.openid.appauth.RedirectUriReceiverActivity"
        tools:node="replace"
        android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.VIEW"/>
        <category android:name="android.intent.category.DEFAULT"/>
        <category android:name="android.intent.category.BROWSABLE"/>
        <data android:scheme="com.example.app"/>
    </intent-filter>
</activity>
```

If an HTTPS redirect URI is required instead of a custom scheme, the same
approach (modifying your AndroidManifest.xml) is used:

```xml
<activity
        android:name=".ui.login.LoginActivity"
        android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.VIEW"/>
        <category android:name="android.intent.category.DEFAULT"/>
        <category android:name="android.intent.category.BROWSABLE"/>
        <data android:scheme="https"
              android:host="app.example.com"
              android:path="/oauth2redirect"/>
    </intent-filter>
</activity>
```

In this case, you will also need to configure the activity's launch mode to
be `singleTask` in your `AndroidManifest.xml`, to ensure that a new instance of
the activity is not created for every authorization response.

```xml
<activity
    android:name=".ui.login.LoginActivity"
    android:launchMode="singleTask">
    ...
</activity>
```

Finally, you need to forward the intent to AppAuth in your activity:

```kotlin
override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    // forward the intent to your authorization handling code
}
```

### Handling the authorization response

This response contains the authorization code, and optionally the original
request and an error. The authorization state can be updated with this response:

```kotlin
fun updateAuthState(response: AuthorizationResponse, ex: AuthorizationException?) {
    authState.update(response, ex)
    if (response != null) {
        // authorization successful
        exchangeAuthorizationCode(response)
    }
}
```

If `performAuthorizationRequest` with a `PendingIntent` was used instead of an `ActivityResultLauncher`, then
the `Intent` that is delivered to the completion handler activity will contain the
response. The response and exception can be extracted from this intent as follows:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val response = AuthorizationResponse.fromIntent(intent)
    val ex = AuthorizationException.fromIntent(intent)

    if (response != null) {
      // authorization succeeded, exchange the auth code for the token
      authState.update(response, ex)
      exchangeAuthorizationCode(response)
    }
}
```

### Exchanging the authorization code

Given a successful authorization response, a token request can be made to
exchange the authorization code for a refresh and/or access token.

```kotlin
fun exchangeAuthorizationCode(authorizationResponse: AuthorizationResponse) {
    val clientAuthentication: ClientAuthentication
    try {
        clientAuthentication = authState.clientAuthentication
    } catch (e: ClientAuthentication.UnsupportedAuthenticationMethod) {
        // This method is not supported by the authorization server
        return
    }

    authService.performTokenRequest(
        authorizationResponse.createTokenExchangeRequest(),
        clientAuthentication
    ) { response, ex ->
        if (response != null) {
            // exchange succeeded
            authState.update(response, ex)
        } else {
            // authorization failed, check ex for more details
        }
    }
}
```

A `ClientAuthentication` instance can be created through a variety of
methods defined in the `net.openid.appauth.ClientAuthentication` class.
This class is used to specify how a client will authenticate to the token
endpoint. RFC 6749 defines
[two types of clients](https://tools.ietf.org/html/rfc6749#section-2.1):
confidential and public. Public clients cannot securely keep a secret, whereas
confidential clients can. The simplest and most common form of authentication
for public clients is to not authenticate at all, but some providers require
a pre-registered client ID:

```kotlin
// No client secret, but client ID is sent
val clientAuth: ClientAuthentication = NoClientAuthentication.INSTANCE
```

If a client secret must be used, then this is also possible, though this is
**not recommended** for native apps as it requires shipping a secret inside your app, which can be extracted.
If you must use a client secret, you should take steps to obfuscate it as much
as possible (e.g. using ProGuard and runtime encryption). A better
alternative is to use dynamic client registration to create a new client, with
a unique secret, for each app instance.

```kotlin
val clientAuth = ClientSecretBasic(MY_CLIENT_SECRET)
```

### Making requests with fresh tokens

`AuthState` can be used to perform actions with a fresh (non-expired) access
token. This will transparently refresh the access token if it has expired,
without any additional effort. The result of any refresh requests will be
persisted to the `AuthState` instance automatically.

```kotlin
authState.performActionWithFreshTokens(authService) { accessToken, idToken, ex ->
    if (ex != null) {
        // negotiation for fresh tokens failed, check ex for more details
        return@performActionWithFreshTokens
    }

    // use the access token to do something useful, e.g.
    // making a request to a resource server
}
```

This action can be simplified further through the use of an `AuthState.AuthStateAction`,
which can be implemented and passed to `performActionWithFreshTokens`.

## More information

- The [demo app](https://github.com/openid/AppAuth-Android/tree/master/app)
- [Javadoc](https://openid.github.io/AppAuth-Android/)
- [RFC 8252 - OAuth 2.0 for Native Apps](https://tools.ietf.org/html/rfc8252)
- [Building secure Android apps with AppAuth (Curity)
  ](https://curity.io/resources/learn/android-appauth-guide/)

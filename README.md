# AppLinks SDK Usage Guide

## Installation

Add the AppLinks SDK dependency to your app's `build.gradle.kts`:

```kotlin
dependencies {
    implementation(project(":lib"))  // or your published artifact
}
```

## Initialization

### 1. Initialize in Application Class (Recommended)

Create an Application class and initialize the SDK once:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        AppLinksSDK.builder(this)
            .autoHandleLinks(true)      // Auto-handle deep links (default: true)
            .enableLogging(BuildConfig.DEBUG)  // Enable logging (default: true)
            .serverUrl("https://applinks.com")  // Your API endpoint
            .apiKey("pk_your_key")       // Your public API key
            .build()
    }
}
```

Don't forget to register your Application class in AndroidManifest.xml:

```xml
<application
    android:name=".MyApplication"
    ...>
```

### 2. Configuration Options

The builder pattern supports the following configuration options:

```kotlin
AppLinksSDK.builder(context)
    .autoHandleLinks(true)        // Automatically launch deep links (default: true)
    .enableLogging(true)          // Enable SDK logging (default: true)
    .serverUrl("https://applinks.com")  // Your backend API URL
    .apiKey("pk_your_key")        // API authentication key (required for production)
    .supportedDomains(setOf("example.com"))  // Domains to handle
    .supportedSchemes(setOf("myapp"))        // Custom schemes to handle
    .installAttributionEnabled(true)         // Report installs to Applinks (default: true)
    .build()
```

## Usage

### Basic Usage (Auto-Handle Links)

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check for deferred deep link
        AppLinksSDK.getInstance().checkForDeferredDeepLink(
            object : AppLinksSDK.DeferredDeepLinkCallback {
                override fun onDeepLinkReceived(
                    deepLink: String, 
                    metadata: Map<String, String>, 
                    handled: Boolean
                ) {
                    // Deep link was found and handled automatically
                    // Use metadata for analytics
                    analytics.track("deferred_deep_link", metadata)
                }
                
                override fun onError(error: String) {
                    // No deferred deep link found
                    // Continue with normal app flow
                }
            }
        )
    }
}
```

### Manual Link Handling

Initialize with `autoHandleLinks = false` to handle links manually:

```kotlin
// In Application class
AppLinksSDK.builder(this)
    .autoHandleLinks(false)  // Disable automatic handling
    .build()

// In your Activity/Fragment
AppLinksSDK.getInstance().checkForDeferredDeepLink(
    object : AppLinksSDK.DeferredDeepLinkCallback {
        override fun onDeepLinkReceived(
            deepLink: String, 
            metadata: Map<String, String>, 
            handled: Boolean
        ) {
            // handled will be false since auto-handling is disabled
            // Parse and handle the link manually
            handleDeepLink(deepLink, metadata)
        }
        
        override fun onError(error: String) {
            // Handle error
        }
    }
)

private fun handleDeepLink(deepLink: String, metadata: Map<String, String>) {
    val uri = Uri.parse(deepLink)
    when (uri.host) {
        "product" -> navigateToProduct(uri.lastPathSegment)
        "category" -> navigateToCategory(uri.getQueryParameter("id"))
        else -> openInWebView(deepLink)
    }
}
```

## Metadata Fields

The metadata map contains attribution information:

- `campaign`: Marketing campaign identifier
- `source`: Traffic source (facebook, email, etc.)
- `customData`: Any custom data passed in the referrer
- `referrerClickTime`: When the Play Store link was clicked
- `installBeginTime`: When the app installation began

## Best Practices

1. **Initialize Once**: Initialize the SDK in your Application class, not in Activities
2. **Handle Both Cases**: Always implement both `onDeepLinkReceived` and `onError`
3. **Track Attribution**: Use the metadata for analytics and attribution
4. **Test Thoroughly**: Test with both install and non-install scenarios
5. **Use BuildConfig**: Use `BuildConfig.DEBUG` for `enableLogging` in production

## Testing

### Test Install Referrer

Use ADB to simulate install referrer:

```bash
adb shell am broadcast -a com.android.vending.INSTALL_REFERRER \
  -n com.yourapp/.YourReceiver \
  --es "referrer" "applinks_link_id=test123&applinks_campaign=test&applinks_source=adb"
```

### Test Deep Links

```bash
# Test with auto-handling enabled
adb shell am start -W -a android.intent.action.VIEW \
  -d "https://yourdomain.com/product/123" com.yourapp

# Test with auto-handling disabled (will just return the link)
# Configure SDK with autoHandleLinks = false first
```

## Install attribution and conversions

The SDK reports the app's install to Applinks once, so the install is credited to the link (and the campaign and ad) that sent the user to the Play Store. Your backend can then report the user's conversions (sign-ups, purchases) for that install, and Applinks sends them to Meta when the user allowed it.

### What is sent, and when

On a fresh install, right after the first deferred deep link lookup (the automatic one on first launch or, with `deferredDeepLinkingEnabled(false)`, your app's first `checkForDeferredDeepLink` call), the SDK sends `POST /api/v1/attributions` with your public API key:

| Field | Value |
|-------|-------|
| `platform` | `android` |
| `bundle_id` | The app's package name. It must be set as the Android bundle ID of your Applinks project |
| `click_id` | The click ID (`ck`) of the Play install referrer. Left out when there is none (an install that didn't come from a link, or from outside the Play Store) |
| `ads_consent` | What you passed to `updateConsent`, `false` until then |
| `limited_data_use` | What you passed to `updateConsent`, `false` until then |
| `advertising_id` | What you passed to `setAdvertisingId`. Left out until then |
| `app_version`, `os_version` | The app's `versionName` and the Android version |

Only the click ID is read from the install referrer: the rest of the referrer never leaves the device. The deep link is delivered exactly as before and doesn't wait for the report, and the `ck` parameter is removed from `LinkHandlingResult.params`.

- The SDK needs an API key (`apiKey`) to report. When the report can't reach Applinks (no network, a server error), it's sent again at the next launch.
- A refused report isn't retried, and the SDK logs why: "App not found" (404) means the package name isn't set as the Android bundle ID of a project you're a member of; 401 means the API key is wrong.
- Apps updated from an SDK version without install attribution don't report: they aren't new installs.
- Turn it off with `.installAttributionEnabled(false)`.

### Conversions: attributionId and your backend

Once the install is reported, `AppLinksSDK.getInstance().attributionId` holds its ID (it's `null` until then). Send it to your backend, for example with the user's profile. Your backend reports the user's conversions with `POST /api/v1/conversions`, its `attribution_id` and your **private** key (`sk_`). The app never reports conversions itself: private keys must never ship in an app.

```kotlin
AppLinksSDK.getInstance().attributionId?.let { attributionId ->
    api.updateProfile(attributionId = attributionId)  // your own backend call
}
```

### Consent

Applinks only sends the install and its conversions to Meta with the user's consent. Call `updateConsent` when the user answers your app's consent prompt, and again whenever they change their choice (in your privacy settings, for example):

```kotlin
AppLinksSDK.getInstance().updateConsent(
    adsConsent = true,        // the user allowed ad measurement (pass true where no prompt is required)
    limitedDataUse = false    // true when the user opted out of sharing under US state privacy laws
)
```

Until it's called, the SDK sends `ads_consent: false` and `limited_data_use: false`, so nothing is sent to Meta. Both values are kept on the device. Called before the install report, they go with it; called after, the SDK sends the change to Applinks (`PATCH /api/v1/attributions/<id>`, with only the fields that changed). New app and Android versions are sent the same way at the first launch after an update.

### Advertising ID

The SDK never reads the Google advertising ID (GAID) itself: it doesn't depend on Google Play services and adds no `AD_ID` permission. When the user allowed tracking, read it in your app and pass it to the SDK; pass `null` to remove it (when the user withdraws consent):

```kotlin
// Off the main thread, with com.google.android.gms:play-services-ads-identifier
// (which adds the AD_ID permission to your manifest)
val info = AdvertisingIdClient.getAdvertisingIdInfo(context)
AppLinksSDK.getInstance().setAdvertisingId(if (info.isLimitAdTrackingEnabled) null else info.id)
```

Applinks keeps the advertising ID only while `ads_consent` is `true`, and deletes it 7 days after the install. An ID set before consent, or kept while consent was withdrawn, is sent again when `updateConsent(adsConsent = true)` is called.

## API Key Security

The SDK enforces API key security:
- **Public keys (`pk_*`)**: Required for mobile applications
- **Private keys (`sk_*`)**: Will throw an `IllegalArgumentException` if used

```kotlin
// ✅ Correct - Public key
AppLinksSDK.builder(context)
    .apiKey("pk_abc123")
    .build()

// ❌ Wrong - Private key (will throw exception)
AppLinksSDK.builder(context)
    .apiKey("sk_xyz789")
    .build()
```

## Troubleshooting

1. **SDK not initialized error**: Make sure you call `AppLinksSDK.builder().build()` before `getInstance()`
2. **No referrer data**: Install referrer is only available for apps installed from Google Play
3. **Links not handling**: Check your AndroidManifest.xml intent filters
4. **Logging not visible**: Ensure `enableLogging` is true in your config
5. **IllegalArgumentException on init**: You're using a private key (sk_*) - switch to a public key (pk_*)

## Migration from Direct Usage

If you were using the SDK directly before:

```kotlin
// Old way
val sdk = AppLinksSDK(context)
sdk.checkForDeferredDeepLink(callback)

// New way
AppLinksSDK.builder(context).build()  // Once in Application
AppLinksSDK.getInstance().checkForDeferredDeepLink(callback)
```
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
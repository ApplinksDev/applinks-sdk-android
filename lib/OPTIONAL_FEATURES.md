# Optional Features in AppLinks SDK

## Navigation Component Integration

The AppLinks SDK includes optional support for Android Navigation Component through the `NavigationLinkHandler` class.

### Requirements

To use `NavigationLinkHandler`, your app must include the following dependencies:

```kotlin
// In your app's build.gradle.kts
dependencies {
    // Required for NavigationLinkHandler
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.6")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.6")
    
    // AppLinks SDK
    implementation("com.applinks:android-sdk:1.0.0")
}
```

### Why It's Optional

The Navigation Component dependencies are marked as `compileOnly` in the SDK to:
1. Keep the SDK lightweight for apps that don't use Navigation Component
2. Avoid version conflicts with apps that use different versions
3. Allow developers to choose their preferred navigation solution

### Usage

If you have the required dependencies, you can use NavigationLinkHandler:

```kotlin
val navigationHandler = NavigationLinkHandler(
    navControllerProvider = { findNavController(R.id.nav_host_fragment) },
    navGraphId = R.navigation.main_navigation
)

AppLinksSDK.getInstance().addCustomHandler(navigationHandler)
```

### What Happens Without Dependencies

If you try to use `NavigationLinkHandler` without the Navigation Component dependencies, you'll get a `NoClassDefFoundError` at runtime. The rest of the SDK will work normally.

### Alternative Handlers

If you don't use Navigation Component, you can still handle links with:
- `UniversalLinkHandler` - For web URLs
- `CustomSchemeHandler` - For custom schemes
- Create your own handler implementing `LinkHandler`

## Future Optional Features

The SDK is designed to support additional optional features in the future, such as:
- Analytics integration
- A/B testing support
- Advanced attribution tracking

Each optional feature will follow the same pattern of using `compileOnly` dependencies to keep the core SDK lean.
# AppLinks Navigation Component Demo

This demo app shows how to integrate AppLinks SDK with Android Navigation Component for seamless deep link handling.

## Features

- **Universal Links**: Handles `https://example.com/...` links
- **Custom Schemes**: Handles `applinks://...` links
- **Deferred Deep Links**: Retrieves links from install referrer
- **Automatic Navigation**: Links automatically navigate to the correct screen
- **Parameter Extraction**: Deep link parameters are passed as navigation arguments

## Testing the Demo

### 1. Install and Launch the App

```bash
./gradlew :app:installDebug
adb shell am start -n app.sweepy.sweepy/com.applinks.android.demo.MainActivity
```

### 2. Test Deep Links

#### Product Screen Deep Links

```bash
# Universal link with product ID
adb shell am start -W -a android.intent.action.VIEW \
  -d "https://example.com/product/999" app.sweepy.sweepy

# Custom scheme with product ID
adb shell am start -W -a android.intent.action.VIEW \
  -d "applinks://product/888" app.sweepy.sweepy

# Universal link with product ID and name
adb shell am start -W -a android.intent.action.VIEW \
  -d "https://example.com/p/777/SuperWidget" app.sweepy.sweepy
```

#### Promo Screen Deep Links

```bash
# Universal link with promo code and discount
adb shell am start -W -a android.intent.action.VIEW \
  -d "https://example.com/promo?code=SUMMER50&discount=50" app.sweepy.sweepy

# Custom scheme with promo code
adb shell am start -W -a android.intent.action.VIEW \
  -d "applinks://promo/FLASH30" app.sweepy.sweepy
```

### 3. Test Deferred Deep Links

To test deferred deep links (links that open after app install):

```bash
# Simulate install referrer with product deep link
adb shell am broadcast -a com.android.vending.INSTALL_REFERRER \
  -n app.sweepy.sweepy/com.applinks.android.demo.InstallReferrerReceiver \
  --es "referrer" "applinks_visit_id=550e8400-e29b-41d4-a716-446655440000"

# Then clear app data to simulate first launch
adb shell pm clear app.sweepy.sweepy

# Launch the app - it should automatically navigate to the linked product
adb shell am start -n app.sweepy.sweepy/com.applinks.android.demo.MainActivity
```

### 4. Check Logs

Watch the logs to see how links are handled:

```bash
adb logcat -s MainActivity:* NavigationLinkHandler:* AppLinksSDK:*
```

## How It Works

1. **MainActivity** sets up the Navigation Component and adds NavigationLinkHandler to AppLinks SDK
2. **NavigationLinkHandler** checks if Navigation Component can handle the URI
3. If a matching deep link is found in the navigation graph, it automatically navigates
4. Parameters from the URI are extracted and passed as navigation arguments
5. Fragments receive the parameters and display the appropriate content

## Navigation Graph Structure

```
main_navigation
├── homeFragment (start destination)
│   ├── action_home_to_product → productFragment
│   └── action_home_to_promo → promoFragment
├── productFragment
│   ├── Deep Link: https://example.com/product/{productId}
│   ├── Deep Link: https://example.com/p/{productId}/{productName}
│   └── Deep Link: applinks://product/{productId}
└── promoFragment
    ├── Deep Link: https://example.com/promo?code={promoCode}&discount={discount}
    └── Deep Link: applinks://promo/{promoCode}
```

## Key Implementation Points

1. **Priority Handling**: NavigationLinkHandler has priority 110, higher than UniversalLinkHandler (100)
2. **Automatic Parameter Extraction**: URI parameters are automatically extracted and passed as Safe Args
3. **Fallback Support**: If Navigation Component can't handle a link, it falls back to other handlers
4. **First Launch Detection**: Deferred deep links are only checked on first app launch

## Debugging Tips

- Use `adb shell dumpsys package app.sweepy.sweepy` to verify intent filters
- Check `navController.currentDestination` to see current navigation state
- Enable logging with AppLinksSDK.builder().enableLogging(true) for detailed logs
- Use Android Studio's Navigation Editor to visualize the navigation graph
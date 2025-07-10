# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AppLinks Android SDK - A library for handling deferred deep links in Android applications, similar to the now-deprecated Firebase Dynamic Links. The SDK uses the Play Install Referrer API to attribute app installs and retrieve deferred deep links.

## Build Commands

```bash
# Build the entire project
./gradlew build

# Build only the library
./gradlew :lib:build

# Build only the demo app
./gradlew :app:build

# Run tests
./gradlew test

# Clean build artifacts
./gradlew clean

# Install demo app on connected device
./gradlew :app:installDebug
```

## Project Architecture

### Module Structure
- **lib/**: The main SDK library module
  - `AppLinksSDK`: Main entry point with singleton pattern and configuration
  - `InstallReferrerManager`: Handles Play Install Referrer API integration
  - `api/LinkApiClient`: HTTP client for fetching link details from server
  - `models/`: Data models for API responses

- **app/**: Demo application showing SDK usage
  - Demonstrates both automatic and manual link handling
  - Shows attribution parameter usage

### Key Design Patterns

1. **Singleton SDK Pattern**: 
   - Initialize once with `AppLinksSDK.builder(context).build()`
   - Access everywhere with `AppLinksSDK.getInstance()`

2. **Builder Pattern Configuration**:
   - Centralized configuration via `AppLinksSDK.builder()`
   - Controls auto-handling, logging, server URL, and API key

3. **Async API Integration**:
   - Uses coroutines for network calls
   - Main-safe callbacks for UI updates

### API Integration Flow

1. App extracts `applinks_link_id` from Play Store referrer
2. SDK calls `GET /api/v1/links/{id}` to fetch full link details
3. SDK validates link expiration and builds deep link URL
4. Either auto-handles the link or returns to callback based on config

## Standard Workflow

1. First think through the problem, read the codebase for relevant files, and write a plan to projectplan.md.
2. The plan should have a list of todo items that you can check off as you complete them
3. Before you begin working, check in with me and I will verify the plan.
4. Then, begin working on the todo items, marking them as complete as you go.
5. Please every step of the way just give me a high level explanation of what changes you made
6. Make every task and code change you do as simple as possible. We want to avoid making any massive or complex changes. Every change should impact as little code as possible. Everything is about simplicity.
7. Finally, add a review section to the projectplan.md file with a summary of the changes you made and any other relevant information.

## Testing the SDK

```bash
# Simulate install referrer with ADB
adb shell am broadcast -a com.android.vending.INSTALL_REFERRER \
  -n com.applinks.android.demo/.YourReceiver \
  --es "referrer" "applinks_link_id=550e8400-e29b-41d4-a716-446655440000&applinks_campaign=test&applinks_source=adb"

# Test deep link handling
adb shell am start -W -a android.intent.action.VIEW \
  -d "https://example.com/product/123" com.applinks.android.demo
```

## Important Implementation Details

- The SDK requires the Play Install Referrer library and works only with apps installed from Google Play
- Network calls use OkHttp with 10-second timeouts
- Link expiration is validated using ISO 8601 timestamps
- Attribution parameters (campaign, source) are preserved in metadata for analytics
- API keys should use public keys (`pk_*`) never private keys (`sk_*`) in mobile apps


# JReleaser Publishing Guide

This document explains how to publish the AppLinks Android SDK to Maven Central using JReleaser.

## Prerequisites

1. **GitHub Repository**: Project must be hosted on GitHub
2. **Central Portal Account**: Create an account at https://central.sonatype.com/
3. **Namespace Verification**: Verify your namespace (e.g., `com.applinks`)
4. **GPG Key**: Generate a GPG key pair for signing artifacts

## Setup

### 1. Create GPG Key

```bash
# Generate a new GPG key
gpg --gen-key

# Export keys for JReleaser
gpg --armor --export YOUR_KEY_ID > public-key.asc
gpg --armor --export-secret-keys YOUR_KEY_ID > private-key.asc

# Upload public key to keyservers
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
```

### 2. Configure Environment Variables

Add to your `~/.gradle/gradle.properties`:

```properties
JRELEASER_GITHUB_TOKEN=your_github_token
JRELEASER_MAVENCENTRAL_USERNAME=your_central_username
JRELEASER_MAVENCENTRAL_TOKEN=your_central_token
JRELEASER_GPG_PASSPHRASE=your_gpg_passphrase
JRELEASER_GPG_SECRET_KEY=-----BEGIN PGP PRIVATE KEY BLOCK-----...-----END PGP PRIVATE KEY BLOCK-----
JRELEASER_GPG_PUBLIC_KEY=-----BEGIN PGP PUBLIC KEY BLOCK-----...-----END PGP PUBLIC KEY BLOCK-----
```

### 3. Update Project Configuration

Update version in `gradle.properties`:
```properties
version=1.0.0
```

Update repository URLs in `jreleaser.yml` to match your actual GitHub repository.

## Publishing Commands

### Build and Test
```bash
./gradlew clean build test
```

### Create Release (Full Process)
```bash
# Build artifacts
./gradlew publishToMavenLocal

# Create GitHub release and publish to Maven Central
./gradlew jreleaserFullRelease
```

### Individual Steps
```bash
# Just create GitHub release
./gradlew jreleaserRelease

# Just deploy to Maven Central
./gradlew jreleaserDeploy

# Just announce (if configured)
./gradlew jreleaserAnnounce
```

### What JReleaser Does

1. **Creates Git tag** from version in gradle.properties
2. **Builds GitHub release** with changelog
3. **Signs artifacts** with your GPG key
4. **Uploads to Maven Central** via Central Portal
5. **Handles all validation** and publishing automatically

## Client Usage

Once published, clients can add the dependency:

```kotlin
dependencies {
    implementation("com.applinks:android-sdk:1.0.0")
}
```

## Troubleshooting

### Common Issues

1. **Signing Failed**: Ensure GPG key is properly formatted and passphrase is correct
2. **Group ID Not Verified**: Contact Sonatype to verify your group ID ownership
3. **Missing POM Information**: All required POM fields must be present
4. **Javadoc Errors**: Fix any Javadoc warnings before publishing

### Validation Requirements

Maven Central requires:
- ✅ Sources JAR
- ✅ Javadoc JAR  
- ✅ GPG signatures on all artifacts
- ✅ Complete POM metadata (name, description, URL, license, developers, SCM)

## Versioning

Follow semantic versioning:
- `1.0.0` - Major release
- `1.0.1` - Patch release
- `1.1.0` - Minor release
- `2.0.0-SNAPSHOT` - Development version

## Security Notes

- Never commit signing keys to version control
- Use Sonatype user tokens instead of passwords
- Keep GPG keys secure and backed up
- Consider using GitHub Actions secrets for CI/CD publishing
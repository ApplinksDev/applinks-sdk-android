#!/bin/bash

# Install required libraries if not present
if ! dpkg -l | grep -q "libnss3\|libx11-6\|libxcb-cursor0"; then
    echo "Installing required libraries for Android emulator..."
    sudo apt-get update
    sudo apt-get install -y \
        libx11-6 libxext6 libxrender1 libxi6 libxcb1 \
        libxcb-cursor0 libxcb-icccm4 libxcb-image0 libxcb-keysyms1 \
        libxcb-randr0 libxcb-render0 libxcb-xfixes0 libxcb-xkb1 \
        libnss3 libnspr4 \
        libvulkan1 libgbm1 libdrm2 \
        libxkbfile1 libxkbcommon0 \
        libpulse0 libasound2t64 \
        xwayland
fi

# Install Android emulator and system image if not already installed
# Note: Using google_apis_playstore image to include Google Play Store
echo "Checking Android SDK components..."
sdkmanager --install "emulator" "system-images;android-34;google_apis_playstore;x86_64" "platform-tools"

# Create AVD if it doesn't exist
if ! avdmanager list avd | grep -q "applinks_test"; then
    echo "Creating AVD..."
    echo "no" | avdmanager create avd -n applinks_test -k "system-images;android-34;google_apis_playstore;x86_64" -d pixel_5
fi

# Create a directory for emulator runtime files
EMULATOR_RUNTIME_DIR="/tmp/android-emulator-runtime"
mkdir -p "$EMULATOR_RUNTIME_DIR"

# Set environment variables
export ANDROID_EMULATOR_HOME="$HOME/.android"
export ANDROID_AVD_HOME="$HOME/.android/avd"
export XDG_RUNTIME_DIR="$EMULATOR_RUNTIME_DIR"

# Force X11 mode since Android emulator doesn't support Wayland
export QT_QPA_PLATFORM=xcb

# Check if we need to start XWayland
if [ -n "$WAYLAND_DISPLAY" ] && [ -z "$DISPLAY" ]; then
    echo "Wayland detected, starting XWayland for X11 compatibility..."
    # Set display to :1 for XWayland
    export DISPLAY=:1
    # Start XWayland in background if not already running
    if ! pgrep -x "Xwayland" > /dev/null; then
        Xwayland :1 &
        sleep 2
    fi
elif [ -z "$DISPLAY" ]; then
    export DISPLAY=:0
fi

echo "Using display: $DISPLAY"

# Start emulator with display
echo "Starting emulator with display support..."
echo "If this fails, run with -no-window flag for headless mode"
emulator -avd applinks_test -gpu swiftshader_indirect -no-audio -no-boot-anim
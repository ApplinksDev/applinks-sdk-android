ARG jdk=21

FROM eclipse-temurin:${jdk}-jdk
ARG android=34
ARG jdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk-linux
ENV PATH=$PATH:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools:${ANDROID_SDK_ROOT}/emulator

RUN apt-get update && apt-get install -y --no-install-recommends \
        curl \
        git \
        git-lfs \
        gnupg \
        openssl \
        unzip \ 
        sudo

# add ubuntu to sudoers
RUN echo "ubuntu ALL=(ALL) NOPASSWD: ALL" >> /etc/sudoers

RUN curl -s https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -o /tmp/tools.zip && \
    unzip -q /tmp/tools.zip -d /tmp && \
    yes | /tmp/cmdline-tools/bin/sdkmanager --sdk_root="${ANDROID_SDK_ROOT}" --licenses && \
    /tmp/cmdline-tools/bin/sdkmanager --sdk_root="${ANDROID_SDK_ROOT}" --install "cmdline-tools;latest" && \
    rm -r /tmp/tools.zip /tmp/cmdline-tools


# Install Gradle
RUN wget https://services.gradle.org/distributions/gradle-8.14.2-bin.zip
RUN unzip -q gradle-8.14.2-bin.zip -d /opt/ && \
    ln -s /opt/gradle-8.14.2/bin/gradle /usr/bin/gradle

RUN chown ubuntu:ubuntu -R "${ANDROID_SDK_ROOT}"

USER ubuntu
RUN mkdir -p /home/ubuntu/.android/ && touch /home/ubuntu/.android/repositories.cfg
RUN yes | sdkmanager --licenses >/dev/null && sdkmanager --install \
        "platforms;android-${android}" \
        "platform-tools"

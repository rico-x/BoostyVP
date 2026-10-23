FROM eclipse-temurin:21-jdk-jammy@sha256:742daacb9316be4b6fc60258f6e4ac2a11882f6c597373c92290a57bfad0d2e0

ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV GRADLE_USER_HOME=/gradle
ENV PATH=/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:${PATH}

RUN apt-get update && apt-get install -y --no-install-recommends curl unzip ca-certificates
RUN curl -fL --retry 3 -o /tmp/android-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip \
    && echo 'e025545c62a8e64c7559119566a569fb1dec5f60  /tmp/android-tools.zip' | sha1sum -c - \
    && mkdir -p /opt/android-sdk/cmdline-tools \
    && unzip -q /tmp/android-tools.zip -d /opt/android-sdk/cmdline-tools \
    && mv /opt/android-sdk/cmdline-tools/cmdline-tools /opt/android-sdk/cmdline-tools/latest
RUN for boosty_sdk_package in 'platforms;android-35' 'build-tools;34.0.0' 'build-tools;35.0.0' 'platform-tools'; do \
    android --sdk=/opt/android-sdk --no-metrics sdk install "$boosty_sdk_package" || exit 1; \
    done

WORKDIR /workspace

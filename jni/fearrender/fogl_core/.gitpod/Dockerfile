FROM ubuntu:24.04

ENV HOME_PATH="/home/gitpod"
ENV SDKMAN_DIR="$HOME_PATH/.sdkman"
ENV ANDROID_SDK_ROOT="/opt/android-sdk"
ENV ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/28.0.13004108"
ENV PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_NDK_HOME:$HOME_PATH/.sdkman/candidates/java/current/bin:$PATH"

# Set noninteractive mode for apt
ENV DEBIAN_FRONTEND=noninteractive

# Install dependencies
RUN apt-get update && apt-get install -y \
    software-properties-common \
    curl \
    git \
    wget \
    unzip \
    ccache \
    build-essential \
    ninja-build && \
    rm -rf /var/lib/apt/lists/*

# Add Kitware's APT repository and install latest CMake
RUN wget -qO - https://apt.kitware.com/keys/kitware-archive-latest.asc | apt-key add - && \
    add-apt-repository 'deb https://apt.kitware.com/ubuntu/ focal main' && \
    apt-get update && apt-get install -y cmake && \
    rm -rf /var/lib/apt/lists/*

# Install SDKMAN! and use it for GraalVM
RUN curl -s "https://get.sdkman.io" | bash && \
    bash -c "source $SDKMAN_DIR/bin/sdkman-init.sh && sdk install java 21.0.5-graalce"

# Install Android SDK and NDK using sdkmanager
RUN mkdir -p $ANDROID_SDK_ROOT && \
    wget -q "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" -O /tmp/cmdline-tools.zip && \
    unzip /tmp/cmdline-tools.zip -d $ANDROID_SDK_ROOT/cmdline-tools && \
    mv $ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools $ANDROID_SDK_ROOT/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip
RUN yes | sdkmanager --licenses && \
    sdkmanager "ndk;28.0.13004108"

# Gitpod-specific setup
RUN echo "export PATH=$PATH" >> $HOME_PATH/.bashrc && \
    echo "export JAVA_HOME=$SDKMAN_DIR/candidates/java/current" >> $HOME_PATH/.bashrc && \
    echo "export ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT" >> $HOME_PATH/.bashrc && \
    echo "export ANDROID_NDK_HOME=$ANDROID_NDK_HOME" >> $HOME_PATH/.bashrc

# Verify installations
RUN java -version && cmake --version && ccache --version && echo "Android NDK: $ANDROID_NDK_HOME"

CMD ["/bin/bash"]

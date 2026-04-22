{
  "name": "Transit Presence",
  "image": "mcr.microsoft.com/devcontainers/base:ubuntu-22.04",
  "features": {
    "ghcr.io/devcontainers/features/java:1": {
      "version": "17",
      "jdkDistro": "temurin"
    },
    "ghcr.io/devcontainers/features/node:1": {
      "version": "20"
    }
  },
  "customizations": {
    "vscode": {
      "extensions": [
        "fwcd.kotlin",
        "vscjava.vscode-gradle",
        "mathiasfrohlich.Kotlin",
        "vscjava.vscode-java-pack"
      ],
      "settings": {
        "java.configuration.runtimes": [
          {
            "name": "JavaSE-17",
            "default": true
          }
        ]
      }
    }
  },
  "postCreateCommand": "bash .devcontainer/setup.sh",
  "remoteEnv": {
    "JAVA_HOME": "/usr/local/sdkman/candidates/java/current",
    "ANDROID_HOME": "/usr/local/android-sdk",
    "ANDROID_SDK_ROOT": "/usr/local/android-sdk",
    "PATH": "${PATH}:/usr/local/android-sdk/cmdline-tools/latest/bin:/usr/local/android-sdk/platform-tools"
  }
}

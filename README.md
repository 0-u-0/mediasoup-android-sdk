# Mediasoup Android SDK

## Docs

## Installation

Available as a Maven package.
```groovy title="build.gradle"
...
dependencies {
    def version = "100.0.3"

    implementation "com.github.0-u-0:mediasoup-android-sdk:$version"
    
}
```

You'll also need JitPack as one of your repositories. In your `settings.gradle` file:

```groovy title="settings.gradle"
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        //...
        maven { url 'https://jitpack.io' }

        // For SNAPSHOT access
        // maven { url 'https://central.sonatype.com/repository/maven-snapshots/' }
    }
}
```

## Usage




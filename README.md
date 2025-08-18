# Mediasoup Android SDK

Pure Java implementation for Mediasoup Android Client

## Installation

Available as a Maven package.
```groovy title="build.gradle"
...
dependencies {
    def version = "0.0.1"

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

* New `Engine`
```java
engine = new Engine(getApplicationContext());
```

* Connect `Signal Server`
```java
engine.connect(signalServer, roomId, peerId);
```

* Init `Player` before using it to play `Video`
```java
engine.initView(player);
```

* Enable and Preview `Camera`
```java
engine.enableCam();
engine.previewCam(player);
```

* Add listener to handle room event
```java
engine.setListener(new Engine.Listener() {
    @Override
    public void onPeer(String peerId, Engine.PeerState state) {
        ...
    }

    @Override
    public void onMedia(String peerId, String consumerId, Engine.MediaKind kind, boolean available) {
        ...
    }
});

```

## Sample App

[mediasoup-android-demo](https://github.com/0-u-0/mediasoup-android-demo)


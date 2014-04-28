# DCEVM

This project is a fork of original [DCEVM](http://ssw.jku.at/dcevm/) project.

The purpose of the project is to maintain enhanced class redefinition functionality for OpenJDK HotSpot 7/8.

## Binaries

 * [Java 7 update 51](https://github.com/dcevm/dcevm/releases/download/light-jdk7u51%2B3/installer-light-jdk7u51.3.jar)
 * [Java 8 update 5](https://github.com/dcevm/dcevm/releases/download/light-jdk8u5%2B36/installer-light-jdk8u5.36.jar)
 * [Java 7 update 51](https://github.com/dcevm/dcevm/releases/download/full-7u51%2B3/installer-full-jdk7u51.3.jar)

Usage:

* Run `java -jar installer.jar`
* Follow the instructions to install DCEVM into existing JDK/JRE


## Supported versions

[pathes/](patches/) contains patches for all supported versions. Each patch is named by concatenating prefix `full` or `light` with the OpenJDK HotSpot tag. `full` patches support full redefenition capabilities (including removal of superclasses, for example). `light` patches are easier to maintain, but they only support limited functionality (generally, additions to class hierarchies are fine, removals are not).

HotSpot tag is the name of the tag in the corresponding HotSpot Mercurial repository ([Java 8](http://hg.openjdk.java.net/jdk8u/jdk8u/hotspot)/[Java 7](http://hg.openjdk.java.net/jdk7u/jdk7u/hotspot)).

## Building

### General Requirements

You need the following software to build DCEVM:

* Java 7 or later. If you intend to run tests, it should be one of the supported versions (see list of [patches/](patches/))
* C++ compiler toolchain (gcc). There is no strict version requirement except that it should be supported by HotSpot build scripts.
* Mercurial

### Compiling DCEVM

* Configure version you want in [gradle.properties](gradle.properties).
* Run `./gradlew patch` to retrieve HotSpot sources and patch them.
* Run `./gradlew compileFastdebug` to build `fastdebug` version or `./gradlew compileProduct` to build `product` version.
* Compiled libraries are placed in `hotspot/build/fastdebug` or `hotspot/build/product`.
* 
### Installing DCEVM

* Replace `libjvm.so/jvm.dll/libjvm.dylib` in the target JRE.
* Run `java -version`, it should include `Dynamic Code Evolution` string.

Or you can install DCEVM using the gradle script:

 * Run `./gradlew installFastdebug -PtargetJre=$JAVA_HOME/jre` or `./gradlew installProduct -PtargetJre=$JAVA_HOME/jre`
 * DCEVM will be installed as "alternative" JVM. To use it, add `-XXaltjvm=dcevm`

### Testing DCEVM

* Configure version you want in [gradle.properties](gradle.properties).
* Set `JAVA_HOME` to point to JDK you want to test against (should be compatible with the version you set in [gradle.properties](gradle.properties)).
* Run `./gradlew patch` to retrieve HotSpot sources and patch them.
* Run `./gradlew test` to run tests.
* Tests reports will be in `dcevm/build/reports/tests/index.html`

To run tests from IDE, you need:
 
 * Run `./gradlew agent:build` to build redefinition agent code.
 * Add JVM argument to use redefinition agent (`-javaagent:agent/build/libs/agent.jar`).
 * Add JVM argument to DCEVM VM if installed side-by-side (`-XXaltjvm=dcevm`).

### Known issues

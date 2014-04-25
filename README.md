# DCEVM

This project is a fork of original [DCEVM](http://ssw.jku.at/dcevm/) project.

## Building

### General Requirements

You need the following software to build DCEVM:

* Java 7 or later. If you intend to run tests, it should be one of the supported versions (see list of [patches/](patches/))
* C++ compiler toolchain (gcc). There is no strict version requirement except that it should be supported by HotSpot build scripts.

### Compiling DCEVM

* Configure version you want in [gradle.properties](gradle.properties).
* Run `./gradlew patch` to retrieve HotSpot sources and patch them.
* Run `./gradlew compileFastdebug` to build `fastdebug` version or `./wgradle compileProduct` to build `product` version.
* Compiled libraries are placed in `hotspot/build/fastdebug` or `hotspot/build/product`.

### Testing DCEVM

* Configure version you want in [gradle.properties](gradle.properties).
* Set `JAVA_HOME` to point to JDK you want to test against (should be compatible with the version you set in [gradle.properties](gradle.properties)).
* Run `./gradlew patch` to retrieve HotSpot sources and patch them.
* Run `./gradlew test` to run tests.
* Tests reports will be in `dcevm/build/reports/tests/index.html`

### Known issues

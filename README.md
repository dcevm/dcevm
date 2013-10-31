How to build the DCEVM library
==============================

Manually
--------

1. Download a copy of [Gradle](http://www.gradle.org/)

   If you are on OSX, `brew install gradle` will do that for you
1. `cd hotswap`
1. `gradle -Pkind=product compileDCEVM`

    This process will take about 20 minutes or so
1. If you are on OSX, the artifacts will live in
   `../build/bsd/bsd_amd64_compiler2/product/*.dylib`

   If you are on Linux, they will be in
   `DCEVM/build/linux/linux_amd64_compiler2/product/*.so`
1. Copy
   `$BUILD/libjsig.`(`so` or `dylib`) to
   `$JAVA_HOME/jre/bin/server`. Of course, you will want to make
   backups of the distributed files, in case DCEVM does something
   goofy for you.


Vagrant
-------

1. Download [VirtualBox](http://www.virtualbox.org/) and install it
1. Download [Vagrant](http://www.vagrantup.com/) and install it
1. Change to the top-level directory where you cloned this repository
1. `vagrant up`
1. Observe the success message that should appear indicating "Your DCEVM is ready to go"
1. `vagrant ssh -c ./build.sh`

Then follow the instructions mentioned above about how to find the
build output and how to install it in your JVM.

Good luck.

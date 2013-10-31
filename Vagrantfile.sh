#! /bin/sh
set -e
#VERBOSE# set -x

## this script runs as root (technically as sudo from vagrant)

# This image was built in .br but 99% of our clients will be in the US
sed -i.bak -e 's,br\.,us.,' /etc/apt/sources.list

## http://files.vagrantup.com/lucid64.box

if test -d /vagrant/apt_archives
then
    # just a trick to speed up rebuilds of this vm
    # dpkg --install --recursive /vagrant/apt_archives
    cat > /etc/apt/sources.list <<EOD
deb     file:/vagrant/apt_archives precise main
deb-src file:/vagrant/apt_archives precise main
EOD
fi
apt-get update
## the force-yes is due to the caching trick above
apt-get build-dep -y --force-yes openjdk-6-jdk
apt-get install   -y --force-yes openjdk-6-jdk
apt-get install   -y --force-yes zip unzip git

cd ~vagrant

if test -f /vagrant/gradle-1.8-bin.zip
then
    cp /vagrant/gradle-1.8-bin.zip .
else
    curl -O http://downloads.gradle.org/distributions/gradle-1.8-bin.zip
fi
unzip -q gradle-1.8-bin.zip

if test -d /vagrant/.git
then
    rsync -Pa /vagrant/.git DCEVM/
    cd DCEVM
    git reset --hard HEAD
    git checkout full-jdk7u45
    cd ..
else
    echo '' >&2
    echo 'I expected this Vagrant script to run from your Git directory;' >&2
    echo 'I will try to work around this problem by downloading the source' >&2
    echo '' >&2
    curl -OL https://github.com/Guidewire/DCEVM/archive/full-jdk7u45.zip
    unzip -q full-jdk7u45.zip
    mv -v DCEVM-full-jdk7u45 DCEVM
fi

cat > ~vagrant/build.sh <<EOD
LANG=C ALT_BOOTDIR=/usr/lib/jvm/java-6-openjdk
export LANG ALT_BOOTDIR

JAVA_HOME=\$ALT_BOOTDIR
export JAVA_HOME

PATH=\$JAVA_HOME/bin:\$PATH
PATH=\$HOME/gradle-1.8/bin:\$PATH

cd \$HOME/DCEVM/hotswap
gradle -s -Pkind=product compileDCEVM
EOD
chmod 755 build.sh

chown -R vagrant ~vagrant

echo 'Your DCEVM is ready to go; issue "vagrant ssh -c ./build.sh"'

cp hotswapinstaller/Installer/dist/Installer.jar dcevm-mac.jar
jar uvf dcevm-mac.jar -C hotswapbinaries/mac/ .
jar uvf dcevm-mac.jar -C hotswapinstaller/Installer/dist data/dcevm.jar

cp hotswapinstaller/Installer/dist/Installer.jar dcevm-win.jar
jar uvf dcevm-win.jar -C hotswapbinaries/win/ .
jar uvf dcevm-win.jar -C hotswapinstaller/Installer/dist data/dcevm.jar

cp hotswapinstaller/Installer/dist/Installer.jar dcevm-linux.jar
jar uvf dcevm-linux.jar -C hotswapbinaries/linux/ .
jar uvf dcevm-linux.jar -C hotswapinstaller/Installer/dist data/dcevm.jar

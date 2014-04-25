set WINDOWS_SDK=c:\Program Files\Microsoft SDKs\Windows\v7.1
call "%WINDOWS_SDK%\bin\setenv.cmd" /%ARCH%

echo Script arguments: %*
set CYGWIN=C:\cygwin
set SCRIPT=%~dp0
set HOTSPOTWORKSPACE=.
%CYGWIN%\bin\bash --login -c 'cd `/usr/bin/cygpath "$SCRIPT"/..` ; /usr/bin/pwd ; /usr/bin/make -C "make" %*'

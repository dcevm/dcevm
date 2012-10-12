set HotSpotMksHome=C:\cygwin\bin
set JAVA_HOME=%cd%\java64
set ORIG_PATH=%PATH%
set path=%JAVA_HOME%\bin;%path%;C:\cygwin\bin

set OrigPath=%cd%
cd make\windows

mkdir %OrigPath%\work64
call create.bat %OrigPath% %OrigPath%\work64 %OrigPath%\java64

set PATH=%ORIG_PATH%
cd %OrigPath%
pause

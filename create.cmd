set HotSpotMksHome=C:\Cygwin\bin
set path=%JAVA_HOME%\bin;C:\Cygwin\bin
call "%VS_VCVARS%\vsvars32.bat"

set OrigPath=%cd%
cd make\windows

mkdir %OrigPath%\work
call create.bat %OrigPath% %OrigPath%\work %OrigPath%\java

cd %OrigPath%
pause

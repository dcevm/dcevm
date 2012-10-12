set HotSpotMksHome=C:\Cygwin\bin
set path=%JAVA_HOME%;C:\Cygwin\bin
call "%VS_VCVARS%\vsvars32.bat"

set OrigPath=%cd%
cd make\windows

call build.bat product compiler1 %OrigPath% %JAVA_HOME%
call build.bat fastdebug compiler1 %OrigPath% %JAVA_HOME%

cd %OrigPath%
pause

set WINDOWS_SDK=c:\Program Files\Microsoft SDKs\Windows\v7.1
call "%WINDOWS_SDK%\bin\setenv.cmd" /%ARCH%

call %VC%\vcvarsall.bat

%VC%\bin\cl /I%JAVA_HOME%\include /I%JAVA_HOME%\include\win32 /I%VC%\include /I%VC%\lib /I%MSDK%\Lib libHelloWorld.c /FelibHelloWorld.dll /LD

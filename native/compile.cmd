set WINDOWS_SDK=c:\Program Files\Microsoft SDKs\Windows\v7.1
call "%WINDOWS_SDK%\bin\setenv.cmd" /%ARCH%

cl /I%JAVA_HOME%\..\include /I%JAVA_HOME%\..\include\win32 natives.c /Febuild\natives.dll /LD

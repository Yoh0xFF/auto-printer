REM install service
TestServ12345.exe //IS//TestServ12345 ^
    --Description "TestServ12345" ^
    --DisplayName "TestServ12345" ^
    --Install "%cd%TestServ12345.exe" ^
    --LogPath "%cd%\logs" ^
    --StdOutput auto ^
    --StdError auto ^
    --Classpath run.jar ^
    --Jvm "%JAVA_HOME%\jre\bin\server\jvm.dll" ^
    --StartMode jvm ^
    --StopMode jvm ^
    --StartPath "%cd%" ^
    --StopPath "%cd%" ^
    --StartClass util.TestService ^
    --StopClass util.TestService ^
    --StartMethod start ^
    --StopMethod stop ^
    --Startup auto

REM start service
TestServ12345.exe //ES//TestServ12345
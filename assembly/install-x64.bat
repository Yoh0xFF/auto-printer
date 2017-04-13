REM install service
AutoPrinterX64.exe //IS//AutoPrinterX64 ^
    --Description "Auto printer" ^
    --DisplayName "AutoPrinter" ^
    --Install "%cd%\AutoPrinterX64.exe" ^
    --LogPath "%cd%\logs" ^
    --StdOutput auto ^
    --StdError auto ^
    --Classpath "run.jar" ^
    --Jvm "%JAVA_HOME%\jre\bin\server\jvm.dll" ^
    --StartMode jvm ^
    --StopMode jvm ^
    --StartPath "%cd%" ^
    --StopPath "%cd%" ^
    --StartClass util.AutoPrinter ^
    --StopClass util.AutoPrinter ^
    --StartMethod start ^
    --StopMethod stop ^
    --Startup auto

REM start service
AutoPrinterX64.exe //ES//AutoPrinterX64
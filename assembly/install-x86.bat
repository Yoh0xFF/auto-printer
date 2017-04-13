REM install service
AutoPrinterX86.exe //IS//AutoPrinterX86 ^
    --Description "Auto printer" ^
    --DisplayName "AutoPrinter" ^
    --Install "%cd%\AutoPrinterX86.exe" ^
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
AutoPrinterX86.exe //ES//AutoPrinterX86
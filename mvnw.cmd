@echo off
if exist "%~dp0mvn.cmd" (
    call "%~dp0mvn.cmd" %*
) else (
    "C:\Program Files\Git\bin\bash.exe" -c "'/c/Program Files/apache-maven-3.9.12/bin/mvn' %*"
)

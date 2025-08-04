if /i "%*" EQU "--help" (
  echo PATCH             Compiles Autobbmd manual command source files.
  exit /b 0
)
if "%*" NEQ "" (
  echo Unexpected parameter.
  exit /b 1
)
echo Compiling patch...
rmdir /Q /S "%workspace%\classes2" >nul 2>nul
javac !compileArgs! -d "%workspace%\classes2" -cp "%workspace%\patch;%workspace%\lib\*" "%workspace%\patch\com\controlj\green\core\process\executable\Autobbmd.java"
if %ERRORLEVEL% NEQ 0 (
  echo Patch compilation unsuccessful.
  exit /b 1
)
for /R "%src%\aces\webctrl\bbmd\resources" %%i in (*.dat) do (
  del /F "%%~fi" >nul 2>nul
)
for /R "%workspace%\classes2\com\controlj\green\core\process\executable" %%i in (*.class) do (
  copy /Y "%%~fi" "%src%\aces\webctrl\bbmd\resources\%%~ni.dat" >nul
)
rmdir /Q /S "%workspace%\classes2" >nul 2>nul
echo Patch compilation successful.
exit /b 0
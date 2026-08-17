:: https://github.com/jomof/android-ccache-example/blob/master/app/src/main/cpp/wrap-ccache.bat

@echo off
setlocal
set "slashed=%*"
set "slashed=%slashed:\=/%"
echo %slashed%
%slashed%

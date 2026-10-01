param([string]$Version = '0.1.0')
$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $ProjectRoot
try {
    mvn -B -ntp verify
    if ($LASTEXITCODE -ne 0) { throw 'Java verification failed' }
    foreach ($Architecture in @('x64', 'Win32')) {
        $NativeBuild = Join-Path $ProjectRoot "build/native-$Architecture"
        cmake -S native -B $NativeBuild -A $Architecture
        if ($LASTEXITCODE -ne 0) { throw "CMake configuration failed: $Architecture" }
        cmake --build $NativeBuild --config Release --parallel 2
        if ($LASTEXITCODE -ne 0) { throw "Native build failed: $Architecture" }
        ctest --test-dir $NativeBuild -C Release --output-on-failure
        if ($LASTEXITCODE -ne 0) { throw "Native tests failed: $Architecture" }
    }
    $PackageInput = Join-Path $ProjectRoot 'build/package-input'
    if (Test-Path $PackageInput) { Remove-Item -LiteralPath $PackageInput -Recurse -Force }
    New-Item -ItemType Directory -Force $PackageInput | Out-Null
    Copy-Item "target/autopunct-java-$Version.jar" $PackageInput
    Copy-Item 'target/lib' $PackageInput -Recurse -Force
    $Image = Join-Path $ProjectRoot 'build/app-image/AutoPunct'
    if (Test-Path $Image) { Remove-Item -LiteralPath $Image -Recurse -Force }
    jpackage --type app-image --name AutoPunct --app-version $Version `
        --input $PackageInput --dest build/app-image `
        --main-jar "autopunct-java-$Version.jar" --main-class ru.autopunct.Main `
        --java-options '-Xms32m' --java-options '-Xmx256m' `
        --java-options '-Dfile.encoding=UTF-8' --java-options '-Dspring.main.headless=false' `
        --vendor 'AutoPunct' --description 'Автоматические запятые без нейросетей'
    if ($LASTEXITCODE -ne 0) { throw 'jpackage failed' }
    New-Item -ItemType Directory -Force "$Image/native/x64", "$Image/native/x86", "$Image/licenses" | Out-Null
    Copy-Item 'build/native-x64/Release/AutoPunctInput.dll' "$Image/native/x64/"
    Copy-Item 'build/native-Win32/Release/AutoPunctInput.dll' "$Image/native/x86/"
    Copy-Item 'LICENSE', 'THIRD_PARTY_NOTICES.md', 'README.md' "$Image/"
    Copy-Item 'build/native-x64/_deps/libime2-src/LICENSE.txt' "$Image/licenses/LibIME2-LGPL-2.1.txt"
    Copy-Item 'build/native-x64/_deps/json-src/LICENSE.MIT' "$Image/licenses/nlohmann-json-MIT.txt"
    $Compiler = (Get-Command ISCC.exe -ErrorAction SilentlyContinue).Source
    if (-not $Compiler) { $Compiler = "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe" }
    & $Compiler "/DVersion=$Version" '/DImageDir=../build/app-image/AutoPunct' packaging/AutoPunct.iss
    if ($LASTEXITCODE -ne 0) { throw 'Installer build failed' }
} finally {
    Pop-Location
}

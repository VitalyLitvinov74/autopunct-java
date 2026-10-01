param([string]$Version = '0.1.0')
$ErrorActionPreference = 'Stop'
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$Installer = Join-Path $ProjectRoot "dist/AutoPunct-$Version-Setup.exe"
$InstallRoot = Join-Path $env:ProgramFiles 'AutoPunct'
$ClassKey = 'Software\Classes\CLSID\{41969CDB-7C7F-4F7B-99C9-2E50695E3CF6}\InprocServer32'
$ApplicationProcess = $null
$Client = $null
$Installed = $false
try {
    $SetupProcess = Start-Process -FilePath $Installer -ArgumentList '/VERYSILENT', '/SUPPRESSMSGBOXES', '/NORESTART', '/SP-' -Wait -PassThru
    if ($SetupProcess.ExitCode -ne 0) { throw "Installer exited with $($SetupProcess.ExitCode)" }
    $Installed = $true
    foreach ($RegistryView in @([Microsoft.Win32.RegistryView]::Registry64, [Microsoft.Win32.RegistryView]::Registry32)) {
        $RegistryRoot = [Microsoft.Win32.RegistryKey]::OpenBaseKey([Microsoft.Win32.RegistryHive]::LocalMachine, $RegistryView)
        $Registered = $RegistryRoot.OpenSubKey($ClassKey)
        if (-not $Registered) { throw "COM registration missing: $RegistryView" }
    if (-not (Test-Path ($Registered.GetValue('')))) { throw "COM DLL missing: $RegistryView" }
        $Registered.Dispose()
        $RegistryRoot.Dispose()
    }
    $Run = Get-ItemProperty -Path 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Run' -ErrorAction SilentlyContinue
    if ($Run -and $Run.PSObject.Properties.Name -contains 'AutoPunct') { throw 'Autostart was enabled unexpectedly' }
    $ApplicationProcess = Start-Process -FilePath "$InstallRoot/AutoPunct.exe" -ArgumentList '--autopunct.tray.enabled=false' -RedirectStandardOutput "$ProjectRoot/build/installed-stdout.log" -RedirectStandardError "$ProjectRoot/build/installed-stderr.log" -PassThru
    $Sid = [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    $Session = [System.Diagnostics.Process]::GetCurrentProcess().SessionId
    $Pipe = "AutoPunct-$Sid-$Session"
    $Client = [System.IO.Pipes.NamedPipeClientStream]::new('.', $Pipe, [System.IO.Pipes.PipeDirection]::InOut, [System.IO.Pipes.PipeOptions]::Asynchronous)
    $Client.Connect(15000)
    $Client.ReadMode = [System.IO.Pipes.PipeTransmissionMode]::Message
    $Text = 'Известно что на улице красивый вид '
    $Request = @{version=1; requestId=701; contextId='installer-test'; revision=1; text=$Text; caretOffset=$Text.Length; application='notepad.exe'} | ConvertTo-Json -Compress
    $Bytes = [System.Text.Encoding]::UTF8.GetBytes($Request)
    $Client.Write($Bytes, 0, $Bytes.Length)
    $Buffer = [byte[]]::new(8192)
    $Cancellation = [System.Threading.CancellationTokenSource]::new(10000)
    $Read = $Client.ReadAsync($Buffer, 0, $Buffer.Length, $Cancellation.Token).GetAwaiter().GetResult()
    $Reply = [System.Text.Encoding]::UTF8.GetString($Buffer, 0, $Read) | ConvertFrom-Json
    Write-Output "Получено байтов ответа: $Read"
    Write-Output ($Reply | ConvertTo-Json -Compress)
    if ($Reply.status -ne 'ok' -or $Reply.requestId -ne 701 -or $Reply.contextId -ne 'installer-test' -or $Reply.commas[0] -ne 8) {
        throw 'Installed application returned an invalid correction'
    }
    $Cancellation.Dispose()
    Write-Output 'Установленная версия: локальная проверка запятых выполнена'
} finally {
    if ($Client) { $Client.Dispose() }
    if ($ApplicationProcess -and -not $ApplicationProcess.HasExited) { Stop-Process -Id $ApplicationProcess.Id -Force }
    if (Test-Path "$ProjectRoot/build/installed-stderr.log") { Get-Content "$ProjectRoot/build/installed-stderr.log" }
    if ($Installed) {
        $Uninstaller = Start-Process -FilePath "$InstallRoot/unins000.exe" -ArgumentList '/VERYSILENT', '/SUPPRESSMSGBOXES', '/NORESTART' -Wait -PassThru
        if ($Uninstaller.ExitCode -ne 0) { throw "Uninstaller exited with $($Uninstaller.ExitCode)" }
        foreach ($RegistryView in @([Microsoft.Win32.RegistryView]::Registry64, [Microsoft.Win32.RegistryView]::Registry32)) {
            $RegistryRoot = [Microsoft.Win32.RegistryKey]::OpenBaseKey([Microsoft.Win32.RegistryHive]::LocalMachine, $RegistryView)
            $Remaining = $RegistryRoot.OpenSubKey($ClassKey)
            if ($Remaining) { $Remaining.Dispose(); throw "COM registration remains: $RegistryView" }
            $RegistryRoot.Dispose()
        }
        Write-Output 'Удаление: регистрации собственных модулей удалены'
    }
}

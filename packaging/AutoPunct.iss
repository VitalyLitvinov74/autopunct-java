#ifndef Version
  #define Version "0.1.0"
#endif
#ifndef ImageDir
  #define ImageDir "../build/app-image/AutoPunct"
#endif
[Setup]
AppId={{DD5D9CE4-5156-4D75-9D1D-1910D4EE6FC4}
AppName=Автозапятые
AppVersion={#Version}
AppPublisher=AutoPunct
DefaultDirName={autopf}\AutoPunct
DefaultGroupName=Автозапятые
OutputDir=../dist
OutputBaseFilename=AutoPunct-{#Version}-Setup
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=admin
Compression=lzma2
SolidCompression=yes
CloseApplications=yes
RestartApplications=no
WizardStyle=modern
DisableProgramGroupPage=yes
LicenseFile=../LICENSE

[Languages]
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"

[Files]
Source: "{#ImageDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Registry]
Root: HKLM64; Subkey: "Software\Classes\CLSID\{{41969CDB-7C7F-4F7B-99C9-2E50695E3CF6}"; Flags: uninsdeletekey
Root: HKLM32; Subkey: "Software\Classes\CLSID\{{41969CDB-7C7F-4F7B-99C9-2E50695E3CF6}"; Flags: uninsdeletekey
Root: HKLM64; Subkey: "Software\Classes\CLSID\{{41969CDB-7C7F-4F7B-99C9-2E50695E3CF6}\InprocServer32"; ValueType: string; ValueData: "{app}\native\x64\AutoPunctInput.dll"
Root: HKLM64; Subkey: "Software\Classes\CLSID\{{41969CDB-7C7F-4F7B-99C9-2E50695E3CF6}\InprocServer32"; ValueType: string; ValueName: "ThreadingModel"; ValueData: "Apartment"
Root: HKLM32; Subkey: "Software\Classes\CLSID\{{41969CDB-7C7F-4F7B-99C9-2E50695E3CF6}\InprocServer32"; ValueType: string; ValueData: "{app}\native\x86\AutoPunctInput.dll"
Root: HKLM32; Subkey: "Software\Classes\CLSID\{{41969CDB-7C7F-4F7B-99C9-2E50695E3CF6}\InprocServer32"; ValueType: string; ValueName: "ThreadingModel"; ValueData: "Apartment"

[Icons]
Name: "{group}\Автозапятые"; Filename: "{app}\AutoPunct.exe"
Name: "{group}\Удалить Автозапятые"; Filename: "{uninstallexe}"

[Run]
Filename: "{app}\AutoPunct.exe"; Description: "Запустить Автозапятые"; Flags: nowait postinstall skipifsilent runasoriginaluser

[UninstallRun]
Filename: "{sys}\regsvr32.exe"; Parameters: "/s /u ""{app}\native\x64\AutoPunctInput.dll"""; Flags: waituntilterminated runhidden
Filename: "{syswow64}\regsvr32.exe"; Parameters: "/s /u ""{app}\native\x86\AutoPunctInput.dll"""; Flags: waituntilterminated runhidden

[UninstallDelete]
Type: files; Name: "{app}\native\x64\AutoPunctInput.dll"
Type: files; Name: "{app}\native\x86\AutoPunctInput.dll"

[Code]
procedure CurStepChanged(CurStep: TSetupStep);
var
  ResultCode: Integer;
begin
  if CurStep = ssPostInstall then begin
    if not Exec(ExpandConstant('{sys}\regsvr32.exe'), ExpandConstant('/s "{app}\native\x64\AutoPunctInput.dll"'), '', SW_HIDE, ewWaitUntilTerminated, ResultCode) then
      RaiseException('Не удалось запустить регистрацию профиля ввода');
    if ResultCode <> 0 then
      RaiseException('Не удалось зарегистрировать 64-разрядный профиль ввода');
    if not Exec(ExpandConstant('{syswow64}\regsvr32.exe'), ExpandConstant('/s "{app}\native\x86\AutoPunctInput.dll"'), '', SW_HIDE, ewWaitUntilTerminated, ResultCode) then
      RaiseException('Не удалось запустить регистрацию 32-разрядного профиля ввода');
    if ResultCode <> 0 then
      RaiseException('Не удалось зарегистрировать 32-разрядный профиль ввода');
  end;
end;

procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep = usUninstall then
    RegDeleteValue(HKCU, 'Software\Microsoft\Windows\CurrentVersion\Run', 'AutoPunct');
end;

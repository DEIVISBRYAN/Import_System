param([string]$JavaHome=$env:JAVA_HOME,[switch]$SkipBuild,[switch]$Resume)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$buildDir=Join-Path $projectRoot 'target/instalador-sqlite-1.1.1'
$outputDir=Join-Path $projectRoot 'output/releases/v1.1.1'
if((Test-Path -LiteralPath $buildDir) -and -not $Resume){throw 'La carpeta de compilacion ya existe. Usa -Resume para continuar.'}
if(-not $JavaHome){throw 'Indica -JavaHome con la carpeta de un JDK 17 o posterior.'}
if(-not $SkipBuild){Push-Location $projectRoot;try { & mvn -B package; if($LASTEXITCODE -ne 0){throw 'Fallo Maven.'} } finally { Pop-Location }}
New-Item -ItemType Directory -Path $buildDir,$outputDir -Force | Out-Null
$inputDir=Join-Path $buildDir 'entrada'
New-Item -ItemType Directory -Path $inputDir -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $projectRoot 'target/crud-persona-spring-1.0.0.jar') -Destination $inputDir
$runtimeDir=Join-Path $buildDir 'runtime'
if(-not (Test-Path -LiteralPath (Join-Path $runtimeDir 'bin/java.exe'))) {
 & "$JavaHome/bin/jlink.exe" --add-modules java.se,jdk.unsupported,jdk.crypto.ec,jdk.zipfs --strip-debug --no-header-files --no-man-pages --compress=2 --output $runtimeDir
 if($LASTEXITCODE -ne 0){throw 'No se pudo generar Java incluido.'}
}
if(-not (Test-Path -LiteralPath (Join-Path $buildDir 'GestionImportaciones/GestionImportaciones.exe'))) {
& "$JavaHome/bin/jpackage.exe" --type app-image --name GestionImportaciones --app-version 1.1.1 --vendor 'Equipo DBQP RPLC ERQZ' --input $inputDir --dest $buildDir --main-jar crud-persona-spring-1.0.0.jar --main-class org.springframework.boot.loader.launch.JarLauncher --runtime-image $runtimeDir --icon (Join-Path $projectRoot 'src/main/resources/images/is-icon.ico') --java-options '-Dfile.encoding=UTF-8' --java-options '-Dapp.demo.users=true' --java-options '-Dspring.config.import=optional:file:$APPDIR/config/database.properties' --java-options '-Dlogging.file.name=$APPDIR/logs/sistema.log'
if($LASTEXITCODE -ne 0){throw 'No se pudo generar el ejecutable.'}
}
$appDir=Join-Path $buildDir 'GestionImportaciones'
Copy-Item -LiteralPath (Join-Path $inputDir 'crud-persona-spring-1.0.0.jar') -Destination (Join-Path $appDir 'app/crud-persona-spring-1.0.0.jar') -Force
$launcherConfig=Join-Path $appDir 'app/GestionImportaciones.cfg'
$launcherText=[System.IO.File]::ReadAllText($launcherConfig)
if(-not $launcherText.Contains('-Dapp.demo.users=true')) {
 [System.IO.File]::WriteAllText($launcherConfig,$launcherText+"`r`njava-options=-Dapp.demo.users=true`r`n",[System.Text.UTF8Encoding]::new($false))
}
New-Item -ItemType Directory -Path (Join-Path $appDir 'app/config') -Force | Out-Null
@'
# Configuracion local de Import System. No requiere un servidor.
app.database.kind=sqlite
app.caja.numero=1
app.demo.users=true
'@ | Set-Content -LiteralPath (Join-Path $appDir 'app/config/database.properties') -Encoding ASCII
$csc=Join-Path $env:WINDIR 'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
$stub=Join-Path $buildDir 'SetupStub.exe'
& $csc /nologo /target:winexe /optimize+ "/out:$stub" "/win32icon:$(Join-Path $projectRoot 'src/main/resources/images/is-icon.ico')" /r:System.dll /r:System.Core.dll /r:System.Drawing.dll /r:System.Windows.Forms.dll /r:System.IO.Compression.dll /r:System.IO.Compression.FileSystem.dll (Join-Path $PSScriptRoot 'installer/Setup.cs')
if($LASTEXITCODE -ne 0){throw 'No se pudo compilar el asistente.'}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$payload=Join-Path $buildDir 'payload.zip'
if(Test-Path -LiteralPath $payload){Remove-Item -LiteralPath $payload -Force}
[System.IO.Compression.ZipFile]::CreateFromDirectory($appDir,$payload,[System.IO.Compression.CompressionLevel]::Optimal,$false)
$installer=Join-Path $outputDir 'Import_System_Setup.exe'
$stream=[System.IO.File]::Create($installer)
try {
 $part=[System.IO.File]::OpenRead($stub);try{$part.CopyTo($stream)}finally{$part.Dispose()}
 $offset=$stream.Position
 $part=[System.IO.File]::OpenRead($payload);try{$length=$part.Length;$part.CopyTo($stream)}finally{$part.Dispose()}
 $writer=New-Object System.IO.BinaryWriter($stream,[System.Text.Encoding]::ASCII,$true)
 $writer.Write([int64]$offset);$writer.Write([int64]$length);$writer.Write([System.Text.Encoding]::ASCII.GetBytes('ISSETUP1'));$writer.Flush();$writer.Dispose()
} finally {$stream.Dispose()}
Write-Output "Instalador: $installer"
Write-Output "Aplicacion portable: $appDir"

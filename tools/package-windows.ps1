[CmdletBinding()]
param(
    [string]$JarPath = "",
    [Parameter(Mandatory)][string]$Destination
)

$ErrorActionPreference = 'Stop'
if ($env:OS -ne 'Windows_NT') { throw 'Build the Windows app image on Windows.' }
$project = Split-Path -Parent $PSScriptRoot
if (!$JarPath) { $JarPath = Join-Path $project 'target\StudyFlow.jar' }
$jar = (Resolve-Path -LiteralPath $JarPath).Path
$icon = (Resolve-Path -LiteralPath (Join-Path $project 'src\main\resources\icons\studyflow.ico')).Path
$version = ([xml](Get-Content -Raw -LiteralPath (Join-Path $project 'pom.xml'))).project.version
$jpackage = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\jpackage.exe' }
            else { (Get-Command jpackage -ErrorAction Stop).Source }
$jdkVersion = & $jpackage --version
if ($LASTEXITCODE -ne 0 -or $jdkVersion -notmatch '^21([.]|$)') {
    throw 'Set JAVA_HOME to a Java 21 JDK containing jpackage.exe.'
}
$destinationInfo = New-Item -ItemType Directory -Path $Destination -Force
$output = $destinationInfo.FullName
if (Test-Path -LiteralPath (Join-Path $output 'StudyFlow')) {
    throw 'Destination already contains StudyFlow. Choose a fresh output directory.'
}
$staging = Join-Path $output ('.package-input-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $staging | Out-Null
try {
    $inputJar = Join-Path $staging 'StudyFlow.jar'
    Copy-Item -LiteralPath $jar -Destination $inputJar
    & $jpackage --type app-image --name StudyFlow --app-version $version --vendor 'StudyFlow project' `
        --description 'A local desktop workspace for academic study' --input $staging --dest $output `
        --main-jar StudyFlow.jar --main-class com.studyflow.Main --icon $icon `
        --java-options '-Dstudyflow.packaged=true' `
        --add-modules java.desktop,java.logging,java.management,java.naming,java.net.http,java.sql,jdk.httpserver,jdk.crypto.ec,jdk.charsets,jdk.unsupported
    if ($LASTEXITCODE -ne 0) { throw "jpackage failed (exit $LASTEXITCODE)." }
    $executable = Get-Item -LiteralPath (Join-Path $output 'StudyFlow\StudyFlow.exe')
    $executable.FullName
} finally {
    # These two exact paths were created by this invocation. No recursive cleanup.
    if (Test-Path -LiteralPath $inputJar) { Remove-Item -LiteralPath $inputJar }
    Remove-Item -LiteralPath $staging
}

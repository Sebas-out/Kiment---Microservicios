param([string]$OutputDirectory = (Join-Path $env:LOCALAPPDATA 'Kiments/auth-keys'))
$ErrorActionPreference = 'Stop'
$keyDirectory = [IO.Path]::GetFullPath($OutputDirectory)
$repoDirectory = [IO.Path]::GetFullPath((Split-Path $PSScriptRoot -Parent))
if ($keyDirectory.StartsWith($repoDirectory, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Las claves deben estar fuera del proyecto.'
}
$privatePath = Join-Path $keyDirectory 'private.pem'
$publicPath = Join-Path $keyDirectory 'public.pem'
if ((Test-Path -LiteralPath $privatePath) -or (Test-Path -LiteralPath $publicPath)) { throw 'Ya existen claves; no se sobrescribirán.' }
New-Item -ItemType Directory -Path $keyDirectory -Force | Out-Null
if ($IsWindows) {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
    & icacls.exe $keyDirectory /inheritance:r /grant:r "${identity}:(OI)(CI)F" | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'No se pudieron restringir los permisos de las claves.' }
}
try {
    $rsa = [Security.Cryptography.RSA]::Create(3072)
    try {
        [IO.File]::WriteAllText($privatePath, $rsa.ExportPkcs8PrivateKeyPem())
        [IO.File]::WriteAllText($publicPath, $rsa.ExportSubjectPublicKeyInfoPem())
    } finally { $rsa.Dispose() }
} catch [System.Management.Automation.RuntimeException] {
    $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java.exe' }
    $genScript = [IO.Path]::GetTempFileName() + '.java'
    $src = @"
import java.nio.file.*; import java.security.*; import java.util.Base64;
public class DevKeyGen {
    public static void main(String[] args) throws Exception {
        var k = KeyPairGenerator.getInstance("RSA"); k.initialize(3072); var p = k.generateKeyPair();
        var mime = Base64.getMimeEncoder(64, new byte[]{10});
        Files.writeString(Path.of(args[0]), "-----BEGIN PRIVATE KEY-----\n" + mime.encodeToString(p.getPrivate().getEncoded()) + "\n-----END PRIVATE KEY-----\n");
        Files.writeString(Path.of(args[1]), "-----BEGIN PUBLIC KEY-----\n" + mime.encodeToString(p.getPublic().getEncoded()) + "\n-----END PUBLIC KEY-----\n");
    }
}
"@
    try {
        [IO.File]::WriteAllText($genScript, $src)
        & $java $genScript $privatePath $publicPath
        if ($LASTEXITCODE -ne 0) { throw 'Falló la generación de claves RSA con Java.' }
    } finally {
        if (Test-Path -LiteralPath $genScript) { Remove-Item -LiteralPath $genScript -Force }
    }
}
Write-Output "JWT_PRIVATE_KEY=$([Uri]::new($privatePath).AbsoluteUri)"
Write-Output "JWT_PUBLIC_KEY=$([Uri]::new($publicPath).AbsoluteUri)"

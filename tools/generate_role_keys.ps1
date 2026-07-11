# Generates encrypted role key hashes and QR payload assets for offline camp builds.
# Plaintext keys stay with the operator only — never commit them to git.

param(
    [Parameter(Mandatory = $true)][string]$AdminKey,
    [Parameter(Mandatory = $true)][string]$TeacherKey,
    [string]$PackageId = "info.nlogn.chat"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$assetsDir = Join-Path $root "app\src\main\assets"
$hashPath = Join-Path $assetsDir "role_keys.enc"
$qrPath = Join-Path $assetsDir "role_qr.enc"

function Get-Sha256Bytes([byte[]]$bytes) {
    return [System.Security.Cryptography.SHA256]::Create().ComputeHash($bytes)
}

function Get-AssetSaltV2([string]$Package) {
    $label = "neon.camp.v2.role.keys.off"
    $pass1 = Get-Sha256Bytes([Text.Encoding]::UTF8.GetBytes($label))
    $twist = [byte[]](0x4e,0x4c,0x4f,0x4f,0x4e,0x2d,0x32,0x30,0x32,0x36,0x2d,0x63,0x61,0x6d,0x70,0x2d)
    $pkg = [Text.Encoding]::UTF8.GetBytes($Package)
    $ms2 = New-Object System.IO.MemoryStream
    $ms2.Write($pass1, 0, $pass1.Length)
    $ms2.Write($twist, 0, $twist.Length)
    $ms2.Write($pkg, 0, $pkg.Length)
    $pass2 = Get-Sha256Bytes($ms2.ToArray())
    $ms3 = New-Object System.IO.MemoryStream
    $ms3.Write($pass2, 0, $pass2.Length)
    $ms3.Write([Text.Encoding]::UTF8.GetBytes("stage-3"), 0, 7)
    $ms3.Write([byte[]](0), 0, 1)
    return Get-Sha256Bytes($ms3.ToArray())
}

function Get-QrSaltV2([byte[]]$AssetSalt) {
    $twist = [byte[]](0x4e,0x4c,0x4f,0x4f,0x4e,0x2d,0x32,0x30,0x32,0x36,0x2d,0x63,0x61,0x6d,0x70,0x2d)
    $ms = New-Object System.IO.MemoryStream
    $ms.Write($AssetSalt, 0, $AssetSalt.Length)
    $ms.Write([Text.Encoding]::UTF8.GetBytes("qr.v2"), 0, 5)
    $ms.Write($twist, 0, $twist.Length)
    return Get-Sha256Bytes($ms.ToArray())
}

function Get-Sha256Hex([string]$text) {
    $bytes = [Text.Encoding]::UTF8.GetBytes($text.Trim())
    $hash = [System.Security.Cryptography.SHA256]::Create().ComputeHash($bytes)
    return ($hash | ForEach-Object { $_.ToString("x2") }) -join ""
}

function Url-Encode([string]$text) {
    return [Uri]::EscapeDataString($text.Trim())
}

function Build-RoleQrPayload([string]$Role, [string]$Key) {
    return "nlogn://role/v1/${Role}#" + (Url-Encode $Key)
}

function Encrypt-Asset([string]$Json, [byte[]]$Key, [string]$OutPath) {
    $plain = [Text.Encoding]::UTF8.GetBytes($Json)
    $aes = [System.Security.Cryptography.AesGcm]::new($Key)
    $iv = New-Object byte[] 12
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($iv)
    $cipher = New-Object byte[] $plain.Length
    $tag = New-Object byte[] 16
    $aes.Encrypt($iv, $plain, $cipher, $tag)
    $payload = New-Object byte[] ($iv.Length + $cipher.Length + $tag.Length)
    [Array]::Copy($iv, 0, $payload, 0, $iv.Length)
    [Array]::Copy($cipher, 0, $payload, $iv.Length, $cipher.Length)
    [Array]::Copy($tag, 0, $payload, $iv.Length + $cipher.Length, $tag.Length)
    New-Item -ItemType Directory -Force -Path (Split-Path $OutPath) | Out-Null
    [IO.File]::WriteAllBytes($OutPath, $payload)
}

$assetSalt = Get-AssetSaltV2 $PackageId
$qrSalt = Get-QrSaltV2 $assetSalt

$hashJson = (@{
    admin = Get-Sha256Hex $AdminKey
    teacher = Get-Sha256Hex $TeacherKey
} | ConvertTo-Json -Compress)

$qrJson = (@{
    admin = (Build-RoleQrPayload "ADMIN" $AdminKey)
    teacher = (Build-RoleQrPayload "TEACHER" $TeacherKey)
} | ConvertTo-Json -Compress)

Encrypt-Asset $hashJson $assetSalt $hashPath
Encrypt-Asset $qrJson $qrSalt $qrPath

Write-Host "Wrote $hashPath"
Write-Host "Wrote $qrPath"
Write-Host "PackageId: $PackageId"

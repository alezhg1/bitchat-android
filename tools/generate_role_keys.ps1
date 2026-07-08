# Generates encrypted role key hashes and QR payload assets for offline camp builds.
# Plaintext keys stay with the operator only — never commit them to git.

param(
    [Parameter(Mandatory = $true)][string]$AdminKey,
    [Parameter(Mandatory = $true)][string]$TeacherKey
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$assetsDir = Join-Path $root "app\src\main\assets"
$hashPath = Join-Path $assetsDir "role_keys.enc"
$qrPath = Join-Path $assetsDir "role_qr.enc"

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

function Encrypt-Asset([string]$Json, [string]$DerivationLabel, [string]$OutPath) {
    $plain = [Text.Encoding]::UTF8.GetBytes($Json)
    $key = [System.Security.Cryptography.SHA256]::Create().ComputeHash(
        [Text.Encoding]::UTF8.GetBytes($DerivationLabel)
    )
    $aes = [System.Security.Cryptography.AesGcm]::new($key)
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

$hashJson = (@{
    admin = Get-Sha256Hex $AdminKey
    teacher = Get-Sha256Hex $TeacherKey
} | ConvertTo-Json -Compress)

$qrJson = (@{
    admin = (Build-RoleQrPayload "ADMIN" $AdminKey)
    teacher = (Build-RoleQrPayload "TEACHER" $TeacherKey)
} | ConvertTo-Json -Compress)

Encrypt-Asset $hashJson "com.neon.android.offline.rolekeys.v1" $hashPath
Encrypt-Asset $qrJson "com.neon.android.offline.roleqr.v1" $qrPath

Write-Host "Wrote $hashPath"
Write-Host "Wrote $qrPath"
Write-Host ""
Write-Host "Admin QR payload (for printing):"
Write-Host (Build-RoleQrPayload "ADMIN" $AdminKey)
Write-Host ""
Write-Host "Teacher QR is shown in-app by admin (Settings -> QR преподавателя)."

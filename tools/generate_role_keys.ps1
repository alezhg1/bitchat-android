# Generates app/src/main/assets/role_keys.enc from operator-supplied long secret keys.
# Run on a build machine; distribute plaintext keys to operators out-of-band only.

param(
    [Parameter(Mandatory = $true)][string]$AdminKey,
    [Parameter(Mandatory = $true)][string]$TeacherKey
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$outPath = Join-Path $root "app\src\main\assets\role_keys.enc"

function Get-Sha256Hex([string]$text) {
    $bytes = [Text.Encoding]::UTF8.GetBytes($text.Trim())
    $hash = [System.Security.Cryptography.SHA256]::Create().ComputeHash($bytes)
    return ($hash | ForEach-Object { $_.ToString("x2") }) -join ""
}

$json = (@{
    admin = Get-Sha256Hex $AdminKey
    teacher = Get-Sha256Hex $TeacherKey
} | ConvertTo-Json -Compress)

$plain = [Text.Encoding]::UTF8.GetBytes($json)
$key = [System.Security.Cryptography.SHA256]::Create().ComputeHash(
    [Text.Encoding]::UTF8.GetBytes("com.neon.android.offline.rolekeys.v1")
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

New-Item -ItemType Directory -Force -Path (Split-Path $outPath) | Out-Null
[IO.File]::WriteAllBytes($outPath, $payload)
Write-Host "Wrote encrypted role key hashes to $outPath"

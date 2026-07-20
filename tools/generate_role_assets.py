#!/usr/bin/env python3
"""Generate encrypted role_keys.enc and role_qr.enc for offline camp builds."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import secrets
import string
from pathlib import Path
from urllib.parse import quote

from cryptography.hazmat.primitives.ciphers.aead import AESGCM

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app" / "src" / "main" / "assets"
KEYS_FILE = ROOT / "tools" / "operator_keys.local.txt"
TWIST = bytes([0x4E, 0x4C, 0x4F, 0x4F, 0x4E, 0x2D, 0x32, 0x30, 0x32, 0x36, 0x2D, 0x63, 0x61, 0x6D, 0x70, 0x2D])
LABEL_V2 = b"neon.camp.v2.role.keys.off"
LABEL_KEYS_V1 = b"com.neon.android.offline.rolekeys.v1"
LABEL_QR_V1 = b"com.neon.android.offline.roleqr.v1"


def sha256(data: bytes) -> bytes:
    return hashlib.sha256(data).digest()


def asset_salt_v2(package_id: str) -> bytes:
    pass1 = sha256(LABEL_V2)
    pass2 = sha256(pass1 + TWIST + package_id.encode("utf-8"))
    return sha256(pass2 + b"stage-3" + bytes([0]))


def qr_salt_v2(asset_salt: bytes) -> bytes:
    return sha256(asset_salt + b"qr.v2" + TWIST)


def sha256_hex(text: str) -> str:
    return hashlib.sha256(text.strip().encode("utf-8")).hexdigest()


def build_qr(role: str, key: str) -> str:
    return f"nlogn://role/v1/{role}#{quote(key.strip(), safe='')}"


def encrypt_asset(payload: str, key: bytes, out_path: Path) -> None:
    iv = secrets.token_bytes(12)
    cipher = AESGCM(key)
    encrypted = cipher.encrypt(iv, payload.encode("utf-8"), None)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_bytes(iv + encrypted)


def new_camp_key(role: str) -> str:
    alphabet = string.ascii_letters + string.digits
    suffix = "".join(secrets.choice(alphabet) for _ in range(48))
    return f"NLOON-{role}-OFFLINE-2026-{suffix}"


def write_operator_keys(admin: str, teacher: str, package_id: str) -> None:
    KEYS_FILE.write_text(
        "\n".join(
            [
                "# CAMP OPERATOR KEYS — local only, never commit",
                f"Admin:   {admin}",
                f"Teacher: {teacher}",
                f"Generated: {__import__('datetime').datetime.now().isoformat()}",
                f"PackageId: {package_id}",
                "",
            ]
        ),
        encoding="utf-8",
    )
    print(f"New operator keys saved to {KEYS_FILE}")


def parse_operator_keys() -> tuple[str, str]:
    text = KEYS_FILE.read_text(encoding="utf-8")
    admin = re.search(r"^Admin:\s*(.+)$", text, re.M)
    teacher = re.search(r"^Teacher:\s*(.+)$", text, re.M)
    if not admin or not teacher:
        raise SystemExit(f"Could not parse keys from {KEYS_FILE}")
    return admin.group(1).strip(), teacher.group(1).strip()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--package-id", default="info.nlogn.chat")
    parser.add_argument("--admin-key")
    parser.add_argument("--teacher-key")
    parser.add_argument("--generate-new-keys", action="store_true")
    args = parser.parse_args()

    if args.generate_new_keys:
        admin = new_camp_key("ADMIN")
        teacher = new_camp_key("TEACHER")
        write_operator_keys(admin, teacher, args.package_id)
    elif args.admin_key and args.teacher_key:
        admin, teacher = args.admin_key.strip(), args.teacher_key.strip()
    elif KEYS_FILE.exists():
        admin, teacher = parse_operator_keys()
    else:
        raise SystemExit("Provide --admin-key/--teacher-key or --generate-new-keys")

    asset_salt = asset_salt_v2(args.package_id)
    qr_salt = qr_salt_v2(asset_salt)

    hash_json = json.dumps({"admin": sha256_hex(admin), "teacher": sha256_hex(teacher)}, separators=(",", ":"))
    qr_json = json.dumps(
        {"admin": build_qr("ADMIN", admin), "teacher": build_qr("TEACHER", teacher)},
        separators=(",", ":"),
    )

    hash_path = ASSETS / "role_keys.enc"
    qr_path = ASSETS / "role_qr.enc"
    encrypt_asset(hash_json, asset_salt, hash_path)
    encrypt_asset(qr_json, qr_salt, qr_path)

    print(f"Wrote {hash_path}")
    print(f"Wrote {qr_path}")
    print(f"PackageId: {args.package_id}")


if __name__ == "__main__":
    main()

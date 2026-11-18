#!/usr/bin/env python3
"""
Carga la llave pública de Google Play en el muro de agradecimientos (tabla public.wall_config).

Dónde está la llave: Play Console → tu app → Monetizar con Play → Configuración de monetización →
"Licencias" → Llave pública codificada en Base64 (empieza con MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA…).
NO es secreta: es la mitad pública. Con ella el servidor comprueba que cada compra del muro la firmó Google.

Uso:
    python3 db/wall/llave_play.py "MIIBIjANBgkq…"      → imprime el SQL
Pega el SQL en Neon → SQL Editor (rol neondb_owner) y córrelo. Solo se hace una vez (o si Google cambia la llave).

Sin dependencias: lee el DER (SubjectPublicKeyInfo → RSAPublicKey) a mano.
"""
import base64
import sys


def _read_tlv(buf: bytes, i: int):
    tag = buf[i]
    i += 1
    length = buf[i]
    i += 1
    if length & 0x80:
        n = length & 0x7F
        length = int.from_bytes(buf[i:i + n], "big")
        i += n
    return tag, buf[i:i + length], i + length


def modulus_from_spki(b64: str) -> tuple[int, int]:
    der = base64.b64decode("".join(b64.split()))
    tag, spki, _ = _read_tlv(der, 0)
    assert tag == 0x30, "no es una SubjectPublicKeyInfo"
    tag, _alg, j = _read_tlv(spki, 0)                 # AlgorithmIdentifier (rsaEncryption)
    tag, bitstr, _ = _read_tlv(spki, j)
    assert tag == 0x03, "falta la llave (BIT STRING)"
    rsa = bitstr[1:]                                  # primer byte = bits sin usar (0)
    tag, seq, _ = _read_tlv(rsa, 0)
    tag, n_bytes, k = _read_tlv(seq, 0)
    tag, e_bytes, _ = _read_tlv(seq, k)
    return int.from_bytes(n_bytes, "big"), int.from_bytes(e_bytes, "big")


def main() -> None:
    if len(sys.argv) != 2:
        print(__doc__)
        sys.exit(1)
    n, e = modulus_from_spki(sys.argv[1])
    if e != 65537:
        sys.exit(f"Exponente inesperado ({e}); la función SQL asume 65537.")
    bits = n.bit_length()
    print(f"-- Llave RSA de {bits} bits (e = 65537)")
    print(f"update public.wall_config set rsa_modulus = {n}, updated_at = now() where id;")


if __name__ == "__main__":
    main()

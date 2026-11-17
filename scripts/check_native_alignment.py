"""Check release native libraries for Google Play TV/64-bit and 16 KB page-size rules.

Usage: python scripts/check_native_alignment.py app/build/outputs/bundle/release/app-release.aab
Accepts an Android App Bundle (base/lib/<abi>/*.so) or an APK (lib/<abi>/*.so).
"""
import struct
import sys
import zipfile
from pathlib import PurePosixPath

PAGE_16K = 16 * 1024
ABI_64 = {'arm64-v8a', 'x86_64'}
ABI_32 = {'armeabi-v7a', 'x86'}
PT_LOAD = 1


def load_alignments(data: bytes) -> list[int]:
    """Return p_align for every PT_LOAD segment of an ELF shared object."""
    if data[:4] != b'\x7fELF':
        raise ValueError('not an ELF file')
    is64 = data[4] == 2
    endian = '<' if data[5] == 1 else '>'
    if is64:
        phoff, = struct.unpack_from(endian + 'Q', data, 0x20)
        phentsize, phnum = struct.unpack_from(endian + 'HH', data, 0x36)
    else:
        phoff, = struct.unpack_from(endian + 'I', data, 0x1C)
        phentsize, phnum = struct.unpack_from(endian + 'HH', data, 0x2A)
    alignments = []
    for index in range(phnum):
        offset = phoff + index * phentsize
        p_type, = struct.unpack_from(endian + 'I', data, offset)
        if p_type != PT_LOAD:
            continue
        p_align, = struct.unpack_from(endian + 'Q', data, offset + 48) if is64 else \
            struct.unpack_from(endian + 'I', data, offset + 28)
        alignments.append(p_align)
    if not alignments:
        raise ValueError('no loadable segments')
    return alignments


def check(archive_path: str) -> list[str]:
    problems: list[str] = []
    libraries: dict[str, set[str]] = {}
    with zipfile.ZipFile(archive_path) as archive:
        for name in archive.namelist():
            path = PurePosixPath(name)
            parts = path.parts
            if path.suffix != '.so' or 'lib' not in parts:
                continue
            lib_index = parts.index('lib')
            if len(parts) != lib_index + 3:
                continue
            abi = parts[lib_index + 1]
            libraries.setdefault(path.name, set()).add(abi)
            if abi in ABI_64:
                try:
                    alignments = load_alignments(archive.read(name))
                except ValueError as error:
                    problems.append(f'{name}: {error}')
                    continue
                if min(alignments) < PAGE_16K:
                    problems.append(f'{name}: LOAD segment alignment {min(alignments)} < {PAGE_16K}')
    for library, abis in sorted(libraries.items()):
        if abis & ABI_32 and not abis & ABI_64:
            problems.append(f'{library}: 32-bit ABI without a 64-bit ABI ({sorted(abis)})')
    return problems


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(__doc__.strip(), file=sys.stderr)
        return 2
    problems = check(argv[1])
    for problem in problems:
        print('NATIVE_ALIGNMENT_ERROR ' + problem, file=sys.stderr)
    if problems:
        return 1
    print('Native libraries: 64-bit coverage and 16 KB LOAD alignment OK')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))

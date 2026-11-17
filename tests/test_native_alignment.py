"""Synthetic ELF/ZIP checks for the release native-library gate; no Android build needed."""
from pathlib import Path
import struct
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "scripts"))
import check_native_alignment as checker  # noqa: E402


def elf(is64: bool, align: int) -> bytes:
    """Minimal little-endian ELF with one PT_LOAD program header."""
    if is64:
        header = bytearray(64)
        header[:6] = b"\x7fELF\x02\x01"
        struct.pack_into("<Q", header, 0x20, 64)
        struct.pack_into("<HH", header, 0x36, 56, 1)
        segment = bytearray(56)
        struct.pack_into("<I", segment, 0, 1)
        struct.pack_into("<Q", segment, 48, align)
    else:
        header = bytearray(52)
        header[:6] = b"\x7fELF\x01\x01"
        struct.pack_into("<I", header, 0x1C, 52)
        struct.pack_into("<HH", header, 0x2A, 32, 1)
        segment = bytearray(32)
        struct.pack_into("<I", segment, 0, 1)
        struct.pack_into("<I", segment, 28, align)
    return bytes(header + segment)


class NativeAlignment(unittest.TestCase):
    def archive(self, entries):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        path = Path(directory.name) / "app.aab"
        with zipfile.ZipFile(path, "w") as archive:
            for name, data in entries.items():
                archive.writestr(name, data)
        return str(path)

    def test_reads_32_and_64_bit_alignment(self):
        self.assertEqual(checker.load_alignments(elf(True, 16384)), [16384])
        self.assertEqual(checker.load_alignments(elf(False, 4096)), [4096])

    def test_accepts_aligned_bundle_with_both_word_sizes(self):
        path = self.archive({
            "base/lib/arm64-v8a/libx.so": elf(True, 16384),
            "base/lib/x86_64/libx.so": elf(True, 65536),
            "base/lib/armeabi-v7a/libx.so": elf(False, 4096),
        })
        self.assertEqual(checker.check(path), [])

    def test_rejects_4k_64_bit_library(self):
        path = self.archive({"lib/arm64-v8a/libx.so": elf(True, 4096)})
        problems = checker.check(path)
        self.assertEqual(len(problems), 1)
        self.assertIn("4096 < 16384", problems[0])

    def test_rejects_32_bit_only_library(self):
        path = self.archive({"lib/armeabi-v7a/libold.so": elf(False, 4096)})
        self.assertIn("without a 64-bit ABI", checker.check(path)[0])

    def test_rejects_non_elf_library(self):
        path = self.archive({"lib/x86_64/libbad.so": b"not elf"})
        self.assertIn("not an ELF file", checker.check(path)[0])

    def test_no_native_code_passes(self):
        self.assertEqual(checker.check(self.archive({"base/dex/classes.dex": b"dex"})), [])


if __name__ == "__main__":
    unittest.main()

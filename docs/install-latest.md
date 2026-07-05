# AcidIsland Latest Install

Build aktif yang harus dipakai:

- `releases/AcidIsland-GUI-latest.jar`
- versi internal: `1.0-syncfix4-optimized2`

Jangan pakai build lama seperti `optimized1`, `syncfix1`, `syncfix2`, `syncfix3`, atau `syncfix4` tanpa suffix `optimized2`.

## Cara pasang

1. Stop server.
2. Hapus semua jar AcidIsland lama dari folder `plugins/`.
3. Jika ada, hapus cache remap Paper:

```bash
rm -f plugins/.paper-remapped/AcidIsland*.jar
```

4. Copy `AcidIsland-GUI-latest.jar` ke folder `plugins/`.
5. Start server.
6. Pastikan log startup menampilkan:

```text
Enabling AcidIsland v1.0-syncfix4-optimized2
```

Kalau log masih menampilkan `1.0-syncfix4-optimized1`, berarti server masih menjalankan jar lama.

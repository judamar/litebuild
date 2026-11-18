package dev.litebuild;

import java.io.ByteArrayOutputStream;

/** Enteros de longitud variable (LEB128) como los que usa el formato Sponge (.schem) en BlockData. */
final class VarInt {
    private VarInt() {
    }

    static void write(ByteArrayOutputStream out, int value) {
        int v = value;
        while ((v & -128) != 0) {
            out.write(v & 127 | 128);
            v >>>= 7;
        }
        out.write(v);
    }

    /** Lee desde data[pos[0]] y avanza pos[0]. Lanza IllegalArgumentException si el dato está truncado o es absurdo. */
    static int read(byte[] data, int[] pos) {
        int value = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            if (pos[0] >= data.length) {
                throw new IllegalArgumentException("BlockData truncado");
            }
            int b = data[pos[0]++] & 0xFF;
            value |= (b & 127) << shift;
            if ((b & 128) == 0) {
                return value;
            }
        }
        throw new IllegalArgumentException("VarInt demasiado largo");
    }
}

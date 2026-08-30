package mapgen.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * AI: Кодек сжатых данных TMX-файлов WorldEd / TileZed. Порт worlded_tmx_codec.py.
 *
 * <p>В одном файле живут два разных формата:
 * <ul>
 *   <li>{@code <data encoding="base64" compression="zlib">} — base64 + ZLIB (RFC 1950, 0x78 0x9C),
 *       полезная нагрузка — GID тайлов;</li>
 *   <li>{@code <pixels>} — base64 + GZIP (RFC 1952, 0x1F 0x8B), полезная нагрузка —
 *       1-based индексы в список {@code <color rgb="R G B"/>} своего {@code <bmp-image>},
 *       0 означает «цвета нет».</li>
 * </ul>
 * В обоих случаях это {@code width * height} значений uint32 little-endian, строки сверху вниз,
 * внутри строки слева направо.
 *
 * <p>Уровень сжатия 6 (Z_DEFAULT_COMPRESSION) и байт OS = 0x0B (Windows) в gzip-заголовке подобраны
 * так, чтобы результат совпадал с оригинальным файлом WorldEd бит в бит: это проверяется
 * round-trip-тестом на шаблоне и заодно страхует от «файл другой, хотя карта та же».
 *
 * <p>Класс без состояния, все методы статические — можно звать из воркеров.
 */
public final class TmxCodec {
    private TmxCodec() {}

    /** Z_DEFAULT_COMPRESSION: именно с ним WorldEd пишет свои файлы. */
    public static final int LEVEL = 6;

    /** zlib OS_CODE на Windows. Java по умолчанию ставит 0, WorldEd — 0x0B. */
    private static final int OS_WINDOWS = 0x0B;

    private static final int GZIP_HEADER_SIZE = 10;

    // ------------------------------------------------------------------ кодирование

    /** {@code <pixels>}: base64 + gzip. */
    public static String encodePixels(int[] values) {
        byte[] raw = pack(values);
        byte[] body = deflate(raw, true);
        byte[] out = new byte[GZIP_HEADER_SIZE + body.length + 8];
        out[0] = 0x1F; out[1] = (byte) 0x8B; out[2] = 8;   // magic + CM = deflate
        // FLG, MTIME, XFL остаются нулями — так же их пишет zlib из-под WorldEd
        out[9] = OS_WINDOWS;
        System.arraycopy(body, 0, out, GZIP_HEADER_SIZE, body.length);
        CRC32 crc = new CRC32();
        crc.update(raw);
        putLE32(out, GZIP_HEADER_SIZE + body.length, crc.getValue());
        putLE32(out, GZIP_HEADER_SIZE + body.length + 4, raw.length);
        return Base64.getEncoder().encodeToString(out);
    }

    /** {@code <data encoding="base64" compression="zlib">}: base64 + zlib. */
    public static String encodeLayer(int[] gids) {
        return Base64.getEncoder().encodeToString(deflate(pack(gids), false));
    }

    // ------------------------------------------------------------------ декодирование

    public static int[] decodePixels(String base64) throws IOException {
        byte[] gz = Base64.getDecoder().decode(strip(base64));
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) {
            return unpack(in.readAllBytes());
        }
    }

    public static int[] decodeLayer(String base64) throws IOException {
        byte[] z = Base64.getDecoder().decode(strip(base64));
        try (InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(z), new Inflater())) {
            return unpack(in.readAllBytes());
        }
    }

    // ------------------------------------------------------------------ флаги GID (стандарт Tiled)

    public static final int FLIPPED_HORIZONTALLY = 0x80000000;
    public static final int FLIPPED_VERTICALLY   = 0x40000000;
    public static final int FLIPPED_DIAGONALLY   = 0x20000000;
    public static final int GID_MASK             = 0x1FFFFFFF;

    public static int tileId(int gid) { return gid & GID_MASK; }

    // ------------------------------------------------------------------ внутреннее

    private static byte[] pack(int[] values) {
        ByteBuffer bb = ByteBuffer.allocate(values.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int v : values) bb.putInt(v);
        return bb.array();
    }

    private static int[] unpack(byte[] raw) {
        ByteBuffer bb = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        int[] out = new int[raw.length / 4];
        for (int i = 0; i < out.length; i++) out[i] = bb.getInt();
        return out;
    }

    /** {@code nowrap = true} — «голый» deflate для gzip, {@code false} — с zlib-обёрткой. */
    private static byte[] deflate(byte[] raw, boolean nowrap) {
        Deflater d = new Deflater(LEVEL, nowrap);
        try {
            d.setInput(raw);
            d.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 8 + 64);
            byte[] buf = new byte[1 << 16];
            while (!d.finished()) out.write(buf, 0, d.deflate(buf));
            return out.toByteArray();
        } finally {
            d.end();
        }
    }

    private static void putLE32(byte[] dst, int at, long value) {
        for (int i = 0; i < 4; i++) dst[at + i] = (byte) (value >>> (8 * i));
    }

    private static String strip(String s) {
        return s.replaceAll("\\s", "");
    }
}

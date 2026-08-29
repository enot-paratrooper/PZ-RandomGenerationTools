package mapgen.util;

import java.util.Arrays;

/**
 * Открытая адресация с линейным пробированием для ключей long, без боксинга.
 * Замена HashSet&lt;Long&gt; в горячих циклах: allowed() опрашивает множество 9 раз на клетку,
 * а рост дерева делает это десятки тысяч раз.
 * Ключ 0 обслуживается отдельным флагом, поэтому 0 в таблице означает "пусто".
 */
public final class LongHashSet {
    private long[] keys;
    private int mask;
    private int size;
    private int limit;
    private boolean hasZero;

    public LongHashSet(int expected) {
        int cap = 8;
        while (cap < expected * 2) cap <<= 1;
        keys = new long[cap];
        mask = cap - 1;
        limit = (int) (cap * 0.6);
    }

    public int size() { return size + (hasZero ? 1 : 0); }

    public boolean contains(long k) {
        if (k == 0) return hasZero;
        int i = index(k);
        for (long cur; (cur = keys[i]) != 0; i = (i + 1) & mask)
            if (cur == k) return true;
        return false;
    }

    /** true, если ключа не было и он добавлен. */
    public boolean add(long k) {
        if (k == 0) {
            if (hasZero) return false;
            hasZero = true;
            return true;
        }
        int i = index(k);
        for (long cur; (cur = keys[i]) != 0; i = (i + 1) & mask)
            if (cur == k) return false;
        keys[i] = k;
        if (++size > limit) grow();
        return true;
    }

    public void clear() {
        Arrays.fill(keys, 0L);
        size = 0;
        hasZero = false;
    }

    private int index(long k) {
        long h = k * 0x9E3779B97F4A7C15L;
        return (int) (h >>> 40) & mask;   // старшие биты перемешанного значения
    }

    private void grow() {
        long[] old = keys;
        keys = new long[old.length << 1];
        mask = keys.length - 1;
        limit = (int) (keys.length * 0.6);
        size = 0;
        for (long k : old) {
            if (k == 0) continue;
            int i = index(k);
            while (keys[i] != 0) i = (i + 1) & mask;
            keys[i] = k;
            size++;
        }
    }
}

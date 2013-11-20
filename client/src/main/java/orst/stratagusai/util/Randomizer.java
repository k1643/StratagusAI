package orst.stratagusai.util;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * singleton source of randomness.
 * 
 */
public class Randomizer {
    private static Random r;


    public static void init(long seed) {
        r = new Random(seed);
    }

    public static Random getRandom() {
        if (r == null) {
            r = new Random();
        }
        return r;
    }

    public static boolean nextBoolean() {
        return getRandom().nextBoolean();
    }

    public static int nextInt(int range) {
        return getRandom().nextInt(range);
    }

    public static float nextFloat() {
        return getRandom().nextFloat();
    }

    public static double nextGaussian() {
        return getRandom().nextGaussian();
    }

    /**
     * select an object from a collection at random.
     * @param c
     * @return
     */
    public static Object select(Collection c) {
        if (c.isEmpty()) {
            return null;
        }
        int n = getRandom().nextInt(c.size());
        Iterator itr = c.iterator();
        for (int i = 0; i < n; i++) {
            itr.next();
        }
        return itr.next();
    }

    public static Object select(List l) {
        if (l.isEmpty()) {
            return null;
        }
        int n = getRandom().nextInt(l.size());
        return l.get(n);
    }

    public static Object select(Object[] l) {
        int n = getRandom().nextInt(l.length);
        return l[n];
    }

    public static void shuffle(List<?> items) {
        Collections.shuffle(items, getRandom());
    }
}

package com.cpt.eventbus_compiler.utils;

import java.util.Collection;
import java.util.Map;

/**
 * 字符串集合判空类
 */
public class EmptyUtils {
    public static boolean isEmpty(CharSequence cs) {
        return cs == null || cs.length() == 0;
    }

    public static boolean isEmpty(Collection<?> co11) {
        return co11 == null || co11.isEmpty();
    }

    public static boolean isEmpty(final Map<?, ?> map) {
        return map == null || map.isEmpty();
    }
}

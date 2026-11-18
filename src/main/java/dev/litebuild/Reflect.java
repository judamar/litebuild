package dev.litebuild;

import java.lang.reflect.Field;

/** Acceso a campos privados de Minecraft probando el nombre de MCP, el de SRG y el ofuscado (según dónde corra el mod). */
final class Reflect {
    private Reflect() {
    }

    static Field find(Class<?> c, String... names) {
        for (String n : names) {
            try {
                Field f = c.getDeclaredField(n);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException | RuntimeException e) {
                // probar el siguiente nombre
            }
        }
        return null;
    }

    static void setInt(Field f, Object target, int value) {
        try {
            if (f != null) {
                f.setInt(target, value);
            }
        } catch (IllegalAccessException e) {
            // sin acceso: la función que depende de este campo simplemente no actúa
        }
    }
}

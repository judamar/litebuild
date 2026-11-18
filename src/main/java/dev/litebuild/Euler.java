package dev.litebuild;

/**
 * Giros del Placement como matrices, con la misma convención que {@link Clipboard#transformed}: primero roll (Z), luego
 * yaw (Y, positivo = horario visto desde arriba) y por último pitch (X); R = Rx(pitch) * Ry(yaw) * Rz(roll). Sirve para el
 * gizmo en modo Local: los ejes propios de la copia son las columnas de R y un giro alrededor de uno de ellos se compone
 * con R y se vuelve a pasar a yaw, pitch y roll. Sin dependencias de Minecraft.
 */
public final class Euler {
    private Euler() {
    }

    public static double[][] rx(double deg) {
        double c = Math.cos(Math.toRadians(deg)), s = Math.sin(Math.toRadians(deg));
        return new double[][] {{1, 0, 0}, {0, c, -s}, {0, s, c}};
    }

    public static double[][] ry(double deg) {
        double c = Math.cos(Math.toRadians(deg)), s = Math.sin(Math.toRadians(deg));
        return new double[][] {{c, 0, -s}, {0, 1, 0}, {s, 0, c}};
    }

    public static double[][] rz(double deg) {
        double c = Math.cos(Math.toRadians(deg)), s = Math.sin(Math.toRadians(deg));
        return new double[][] {{c, -s, 0}, {s, c, 0}, {0, 0, 1}};
    }

    public static double[][] mul(double[][] a, double[][] b) {
        double[][] r = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                for (int k = 0; k < 3; k++) {
                    r[i][j] += a[i][k] * b[k][j];
                }
            }
        }
        return r;
    }

    /** R = Rx(pitch) * Ry(yaw) * Rz(roll). */
    public static double[][] matrix(double yaw, double pitch, double roll) {
        return mul(rx(pitch), mul(ry(yaw), rz(roll)));
    }

    /** Los ejes propios: axes[i] = R * e_i (la columna i de R). */
    public static double[][] axes(double[][] r) {
        double[][] a = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int k = 0; k < 3; k++) {
                a[i][k] = r[k][i];
            }
        }
        return a;
    }

    /** {yaw, pitch, roll} en grados de una matriz de giro (yaw queda entre -90 y 90; pitch y roll, entre -180 y 180). */
    public static double[] angles(double[][] r) {
        double yaw = Math.asin(Math.max(-1, Math.min(1, -r[0][2])));
        double pitch, roll;
        if (Math.abs(Math.cos(yaw)) > 1e-6) {
            roll = Math.atan2(-r[0][1], r[0][0]);
            pitch = Math.atan2(-r[1][2], r[2][2]);
        } else {
            roll = 0; // cardán bloqueado: todo el giro restante va al pitch
            pitch = Math.atan2(r[2][1], r[1][1]);
        }
        return new double[] {Math.toDegrees(yaw), Math.toDegrees(pitch), Math.toDegrees(roll)};
    }
}

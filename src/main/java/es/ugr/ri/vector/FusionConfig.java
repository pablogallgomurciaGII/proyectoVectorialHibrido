package es.ugr.ri.vector;

import java.util.Locale;

/**
 * Parámetros de la fusión híbrida (actividad 3), elegibles al ejecutar sin editar el código:
 *   -Drrf.c=60          constante c de RRF
 *   -Drrf.alpha=0.5     peso de BM25 en el RRF ponderado (el vector recibe 1 − alpha)
 *   -Dlinear.alpha=0.5  peso de BM25 en la fusión lineal (el vector recibe 1 − alpha)
 *   -Dfusion.depth=20   películas que se toman de cada ranking antes de fusionar (como mínimo k)
 */
public record FusionConfig(int c, double rrfAlpha, double linearAlpha, int depth) {

    public FusionConfig {
        if (c < 1) throw new IllegalArgumentException("rrf.c debe ser positivo");
        if (rrfAlpha < 0 || rrfAlpha > 1) throw new IllegalArgumentException("rrf.alpha debe estar en [0, 1]");
        if (linearAlpha < 0 || linearAlpha > 1) throw new IllegalArgumentException("linear.alpha debe estar en [0, 1]");
        if (depth < 1) throw new IllegalArgumentException("fusion.depth debe ser positivo");
    }

    public static FusionConfig fromProperties() {
        return new FusionConfig(
                Integer.getInteger("rrf.c", 60),
                Double.parseDouble(System.getProperty("rrf.alpha", "0.5")),
                Double.parseDouble(System.getProperty("linear.alpha", "0.5")),
                Integer.getInteger("fusion.depth", 20));
    }

    /** Valores por defecto: equivalentes a la fusión RRF original (c = 60, 20 por ranking). */
    public static FusionConfig defaults() { return new FusionConfig(60, 0.5, 0.5, 20); }

    @Override public String toString() {
        return String.format(Locale.ROOT, "c=%d, rrf.alpha=%.2f, linear.alpha=%.2f, profundidad=%d",
                c, rrfAlpha, linearAlpha, depth);
    }
}
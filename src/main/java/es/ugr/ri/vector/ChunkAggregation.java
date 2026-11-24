package es.ugr.ri.vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Actividad 2: cómo convertir un ranking de FRAGMENTOS en un ranking de PELÍCULAS.
 *
 * Los fragmentos candidatos se agrupan por movie_id y cada película recibe una única puntuación:
 *  - MAX:       la del mejor fragmento recuperado (agregación de referencia, apartado 5.4).
 *  - TOP2_MEAN: la media de los DOS mejores fragmentos recuperados. Si entre los candidatos solo
 *               aparece UN fragmento de la película, el segundo valor es la puntuación del ÚLTIMO
 *               candidato recuperado.
 *
 * Por qué el último candidato: si el segundo fragmento no está entre los candidatos, su puntuación
 * es menor o igual que la del último candidato (si no, el kNN lo habría devuelto). Es la cota más
 * optimista posible para el fragmento que falta. Usar el propio mejor fragmento como segundo valor
 * (primera versión) favorecía a las películas con un solo fragmento recuperado frente a las que
 * tenían dos buenos; promediar con 0 castigaría en exceso las sinopsis cortas.
 *
 * Importante: la agregación solo ve los fragmentos CANDIDATOS devueltos por kNN, no todos los
 * fragmentos de la película. Con pocos candidatos, una película puede tener un único fragmento
 * recuperado aunque en el índice tenga varios.
 *
 * Se selecciona al ejecutar con -Dagg=max | -Dagg=top2.
 */
public enum ChunkAggregation {
    MAX("VECTOR-MAX"),
    TOP2_MEAN("VECTOR-TOP2");

    private final String label;

    ChunkAggregation(String label) { this.label = label; }

    /** Nombre del sistema en las tablas de resultados. */
    public String label() { return label; }

    public static ChunkAggregation fromProperty() {
        return parse(System.getProperty("agg", "max"));
    }

    public static ChunkAggregation parse(String value) {
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "max" -> MAX;
            case "top2", "top2_mean", "top2-mean", "media2" -> TOP2_MEAN;
            default -> throw new IllegalArgumentException("Agregación desconocida: " + value + " (use max o top2)");
        };
    }

    /**
     * Agrupa los fragmentos por película, puntúa cada película y devuelve las k mejores,
     * sin duplicados, ordenadas por puntuación descendente (desempate por id para que el orden sea estable).
     * Cada película conserva como evidencia su MEJOR fragmento (chunkId y texto).
     */
    public List<RankedMovie> aggregate(List<ChunkHit> hits, int k) {
        if (k <= 0 || hits.isEmpty()) return List.of();

        // LinkedHashMap: conserva el orden de primera aparición (útil al depurar).
        Map<String, List<ChunkHit>> byMovie = new LinkedHashMap<>();
        double lastCandidate = Double.POSITIVE_INFINITY;   // puntuación del peor fragmento recuperado
        for (ChunkHit h : hits) {
            byMovie.computeIfAbsent(h.movieId(), id -> new ArrayList<>()).add(h);
            lastCandidate = Math.min(lastCandidate, h.score());
        }

        List<RankedMovie> movies = new ArrayList<>(byMovie.size());
        for (List<ChunkHit> fragments : byMovie.values()) {
            // Lucene ya los devuelve ordenados, pero no dependemos de ello.
            fragments.sort(Comparator.comparingDouble(ChunkHit::score).reversed());
            ChunkHit best = fragments.get(0);
            double score = switch (this) {
                case MAX -> best.score();
                case TOP2_MEAN -> (best.score()
                        + (fragments.size() >= 2 ? fragments.get(1).score() : lastCandidate)) / 2.0;
            };
            movies.add(new RankedMovie(best.movieId(), best.title(), score, best.chunkId(), best.text()));
        }

        movies.sort(Comparator.comparingDouble(RankedMovie::score).reversed().thenComparing(RankedMovie::id));
        return List.copyOf(movies.subList(0, Math.min(k, movies.size())));
    }

    /** Número de películas distintas entre los fragmentos candidatos (apartado 1 de la actividad 2). */
    public static long distinctMovies(List<ChunkHit> hits) {
        return hits.stream().map(ChunkHit::movieId).distinct().count();
    }
}
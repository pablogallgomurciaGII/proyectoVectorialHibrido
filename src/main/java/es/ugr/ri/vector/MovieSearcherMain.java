package es.ugr.ri.vector;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Búsqueda: sample <consulta...> o <directorio-indices> <consulta...>.
 *
 * Propiedades:
 *   -Dk=10              películas por ranking
 *   -Dcandidates=100    fragmentos candidatos del kNN
 *   -Dshow.chunks=0     si es > 0, muestra los N primeros fragmentos SIN agrupar (actividad 2.1)
 *   -Dexcerpt.words=30  palabras del fragmento ganador que se muestran (0 = ninguna; actividad 2.5)
 *   -Drrf.c, -Drrf.alpha, -Dlinear.alpha, -Dfusion.depth   fusión híbrida (ver FusionConfig)
 *   -Dshow.linear=true  desglose de la fusión lineal: puntuaciones en bruto, normalizadas y suma sin normalizar
 */
public final class MovieSearcherMain {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("Uso: MovieSearcherMain <sample|directorio-indices> <consulta...>");
        boolean sample = args[0].equalsIgnoreCase("sample");
        RunConfig config = RunConfig.fromProperties();
        int showChunks = Integer.getInteger("show.chunks", 0);
        int excerptWords = Integer.getInteger("excerpt.words", 30);
        FusionConfig fusion = FusionConfig.fromProperties();

        try (IndexDirectories indexes = sample ? IndexDirectories.memory() : IndexDirectories.disk(Path.of(args[0]))) {
            if (sample) new MovieIndexer().build(MovieData.sample(), indexes,
                    new TextChunker(config.chunkWords(), config.chunkOverlap()), new EmbeddingService());
            String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
            System.out.printf(Locale.ROOT, "Consulta: \"%s\" | k=%d | candidates=%d | fusión: %s%n",
                    query, config.k(), config.candidateChunks(), fusion);

            try (MovieSearcher searcher = new MovieSearcher(indexes, new EmbeddingService())) {
                if (showChunks > 0) showRawChunks(searcher.rawChunks(query, showChunks), excerptWords);
                show("BM25", searcher.bm25(query, config.k()), 0);
                show(ChunkAggregation.MAX.label(),
                        searcher.vectorChunks(query, config.candidateChunks(), config.k(), ChunkAggregation.MAX), excerptWords);
                show(ChunkAggregation.TOP2_MEAN.label(),
                        searcher.vectorChunks(query, config.candidateChunks(), config.k(), ChunkAggregation.TOP2_MEAN), excerptWords);
                show("GLOBAL", searcher.vectorGlobal(query, config.k()), 0);
                show("RRF", searcher.hybridRrf(query, config.candidateChunks(), config.k(), fusion), 0);
                show("RRF-POND", searcher.hybridWeightedRrf(query, config.candidateChunks(), config.k(), fusion), 0);
                show("LINEAL", searcher.hybridLinear(query, config.candidateChunks(), config.k(), fusion), 0);
                if (Boolean.getBoolean("show.linear")) showLinearDetail(searcher, query, config, fusion);
            }
        }
    }

    /** Ranking de fragmentos sin agrupar: una película puede ocupar varias posiciones. */
    private static void showRawChunks(List<ChunkHit> hits, int excerptWords) {
        System.out.printf("%nFRAGMENTOS SIN AGRUPAR (primeros %d)%n", hits.size());
        for (ChunkHit h : hits) {
            System.out.printf(Locale.ROOT, "%3d %-22s score=%.4f id=%s chunk=%d%n",
                    h.rank(), cut(h.title(), 22), h.score(), h.movieId(), h.chunkId());
            if (excerptWords > 0) System.out.println("      \"" + firstWords(h.text(), excerptWords) + "\"");
        }
        System.out.printf("=> %d fragmentos, %d películas distintas%n",
                hits.size(), ChunkAggregation.distinctMovies(hits));
    }

    private static void show(String method, List<RankedMovie> results, int excerptWords) {
        System.out.println("\n" + method);
        for (int i = 0; i < results.size(); i++) {
            RankedMovie r = results.get(i);
            System.out.printf(Locale.ROOT, "%2d %-22s score=%.4f id=%s chunk=%d%n",
                    i + 1, cut(r.title(), 22), r.score(), r.id(), r.chunkId());
            if (excerptWords > 0 && r.excerpt() != null && !r.excerpt().isBlank())
                System.out.println("     \"" + firstWords(r.excerpt(), excerptWords) + "\"");
        }
    }

    /**
     * Desglose de la fusión lineal (actividad 3.2b): para cada película del ranking LINEAL,
     * puntuación BM25 y vectorial en bruto y tras min-max, puntuación final y la suma SIN normalizar.
     * "—" indica que la película no está entre las fusion.depth primeras de ese sistema (normalizada = 0).
     */
    private static void showLinearDetail(MovieSearcher searcher, String query, RunConfig config, FusionConfig f) throws Exception {
        int depth = Math.max(config.k(), f.depth());
        List<RankedMovie> bm25 = searcher.bm25(query, depth);
        List<RankedMovie> vector = searcher.vectorChunks(query, config.candidateChunks(), depth);
        var nb = RankFusion.minMax(bm25);
        var nv = RankFusion.minMax(vector);
        java.util.Map<String, Double> rb = new java.util.HashMap<>(), rv = new java.util.HashMap<>();
        bm25.forEach(m -> rb.putIfAbsent(m.id(), m.score()));
        vector.forEach(m -> rv.putIfAbsent(m.id(), m.score()));
        double a = f.linearAlpha();

        System.out.printf(Locale.ROOT, "%nDESGLOSE LINEAL (alpha=%.2f; min-max sobre las %d primeras de cada sistema)%n", a, depth);
        System.out.printf(Locale.ROOT, "   BM25: max=%.4f min=%.4f | VECTOR: max=%.4f min=%.4f%n",
                java.util.Collections.max(rb.values()), java.util.Collections.min(rb.values()),
                java.util.Collections.max(rv.values()), java.util.Collections.min(rv.values()));
        System.out.printf("%2s %-22s %8s %6s %8s %6s %7s %10s%n",
                "", "película", "BM25", "norm", "VECTOR", "norm", "S", "sin norm.");
        List<RankedMovie> fused = RankFusion.linear(bm25, vector, a, config.k());
        for (int i = 0; i < fused.size(); i++) {
            String id = fused.get(i).id();
            Double b = rb.get(id), v = rv.get(id);
            double raw = a * (b == null ? 0 : b) + (1 - a) * (v == null ? 0 : v);
            System.out.printf(Locale.ROOT, "%2d %-22s %8s %6.3f %8s %6.3f %7.4f %10.4f%n",
                    i + 1, cut(fused.get(i).title(), 22),
                    b == null ? "—" : String.format(Locale.ROOT, "%.4f", b), nb.getOrDefault(id, 0.0),
                    v == null ? "—" : String.format(Locale.ROOT, "%.4f", v), nv.getOrDefault(id, 0.0),
                    fused.get(i).score(), raw);
        }
    }

    static String firstWords(String text, int n) {
        String[] w = text.trim().split("\\s+");
        if (w.length <= n) return text.trim();
        return String.join(" ", Arrays.copyOfRange(w, 0, n)) + " …";
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
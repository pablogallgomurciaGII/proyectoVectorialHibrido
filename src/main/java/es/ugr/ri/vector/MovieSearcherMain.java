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

    static String firstWords(String text, int n) {
        String[] w = text.trim().split("\\s+");
        if (w.length <= n) return text.trim();
        return String.join(" ", Arrays.copyOfRange(w, 0, n)) + " …";
    }

    private static String cut(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
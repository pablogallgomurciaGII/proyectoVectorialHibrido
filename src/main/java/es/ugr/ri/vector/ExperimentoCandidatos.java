package es.ugr.ri.vector;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Actividad 2, punto 4: efecto del número de fragmentos candidatos del kNN.
 *
 * Uso:
 *   ExperimentoCandidatos <sample|directorio-indices> <consulta...>   → detalle de UNA consulta
 *   ExperimentoCandidatos <sample|directorio-indices>                 → resumen sobre todas las consultas
 *
 * Propiedades:
 *   -Dcandidates.list=20,100,300   valores a comparar (puede incluir uno mayor que el nº de fragmentos)
 *   -Dk=10                         películas en el top-k
 *   -Dreps=5                       repeticiones para medir el tiempo (se usa la mediana)
 *   -Dqueries=ruta.tsv             consultas para el modo resumen (por defecto las del guion)
 *
 * El tiempo medido es SOLO la búsqueda kNN + lectura de fragmentos + agregación MAX.
 * El embedding de la consulta se calcula una vez antes y no se incluye: no depende de candidates.
 * Antes de medir se hace una búsqueda de calentamiento (la primera es más lenta por la JVM y las cachés).
 */
public final class ExperimentoCandidatos {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) throw new IllegalArgumentException(
                "Uso: ExperimentoCandidatos <sample|directorio-indices> [consulta...]");
        boolean sample = args[0].equalsIgnoreCase("sample");
        RunConfig config = RunConfig.fromProperties();
        int[] values = Arrays.stream(System.getProperty("candidates.list", "20,100,300").split(","))
                .map(String::trim).mapToInt(Integer::parseInt).toArray();
        int k = config.k();
        int reps = Math.max(1, Integer.getInteger("reps", 5));

        try (IndexDirectories indexes = sample ? IndexDirectories.memory() : IndexDirectories.disk(Path.of(args[0]))) {
            if (sample) new MovieIndexer().build(MovieData.sample(), indexes,
                    new TextChunker(config.chunkWords(), config.chunkOverlap()), new EmbeddingService());
            try (MovieSearcher searcher = new MovieSearcher(indexes, new EmbeddingService())) {
                System.out.printf("Fragmentos en el índice: %d | k=%d | repeticiones=%d%n",
                        searcher.chunkCount(), k, reps);
                if (args.length > 1) {
                    String query = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                    detail(searcher, query, values, k, reps);
                } else {
                    Path file = Path.of(System.getProperty("queries", sample
                            ? "src/main/resources/evaluation/sample_queries.tsv"
                            : "src/main/resources/evaluation/queries.tsv"));
                    summary(searcher, loadQueries(file), values, k, reps);
                }
            }
        }
    }

    /** Una consulta: para cada valor de candidates, nº real, películas distintas, tiempo y top-k. */
    private static void detail(MovieSearcher searcher, String query, int[] values, int k, int reps) throws Exception {
        System.out.printf("Consulta: \"%s\"%n", query);
        float[] qv = searcher.embedQuery(query);
        List<String> previousMax = null;
        for (int c : values) {
            Run run = run(searcher, qv, c, k, reps);
            System.out.printf(Locale.ROOT, "%ncandidates=%d → fragmentos recuperados=%d%s | películas distintas=%d | tiempo=%.2f ms%n",
                    c, run.hits.size(), run.hits.size() < c ? " (limitado por el índice)" : "",
                    ChunkAggregation.distinctMovies(run.hits), run.millis);
            printTop("  MAX ", run.max);
            printTop("  TOP2", run.top2);
            if (previousMax != null)
                System.out.printf("  Top-%d MAX en común con el valor anterior: %d de %d%n",
                        k, common(previousMax, ids(run.max)), Math.max(previousMax.size(), run.max.size()));
            previousMax = ids(run.max);
        }
    }

    /** Todas las consultas: medias por valor de candidates, comparando con el mayor valor como referencia. */
    private static void summary(MovieSearcher searcher, Map<String, String> queries, int[] values, int k, int reps) throws Exception {
        int ref = Arrays.stream(values).max().orElseThrow();
        System.out.printf("Consultas: %d | referencia para comparar el top-k: candidates=%d%n%n", queries.size(), ref);

        int n = values.length;
        double[] hitsSum = new double[n], distinctSum = new double[n], timeSum = new double[n];
        double[] overlapMax = new double[n], overlapTop2 = new double[n];
        int[] fewerThanK = new int[n];

        for (String query : queries.values()) {
            float[] qv = searcher.embedQuery(query);
            Run reference = run(searcher, qv, ref, k, reps);
            for (int i = 0; i < n; i++) {
                Run r = values[i] == ref ? reference : run(searcher, qv, values[i], k, reps);
                hitsSum[i] += r.hits.size();
                distinctSum[i] += ChunkAggregation.distinctMovies(r.hits);
                timeSum[i] += r.millis;
                overlapMax[i] += overlap(ids(r.max), ids(reference.max));
                overlapTop2[i] += overlap(ids(r.top2), ids(reference.top2));
                if (r.max.size() < k) fewerThanK[i]++;
            }
        }
        int q = queries.size();
        System.out.printf("%11s %12s %12s %10s %14s %15s %14s%n",
                "candidates", "fragmentos", "películas", "ms", "top-k MAX =ref", "top-k TOP2 =ref", "con < k pelis");
        for (int i = 0; i < n; i++)
            System.out.printf(Locale.ROOT, "%11d %12.1f %12.1f %10.2f %13.0f%% %14.0f%% %14d%n",
                    values[i], hitsSum[i] / q, distinctSum[i] / q, timeSum[i] / q,
                    100 * overlapMax[i] / q, 100 * overlapTop2[i] / q, fewerThanK[i]);
        System.out.println("\nfragmentos/películas = media por consulta; ms = mediana por consulta, promediada;");
        System.out.println("top-k =ref = % del top-k que coincide con el obtenido con candidates=" + ref + ".");
    }

    private record Run(List<ChunkHit> hits, List<RankedMovie> max, List<RankedMovie> top2, double millis) {}

    private static Run run(MovieSearcher searcher, float[] qv, int candidates, int k, int reps) throws Exception {
        ChunkAggregation.MAX.aggregate(searcher.searchChunks(qv, candidates), k); // calentamiento
        double[] times = new double[reps];
        List<ChunkHit> hits = null;
        List<RankedMovie> max = null;
        for (int r = 0; r < reps; r++) {
            long t0 = System.nanoTime();
            hits = searcher.searchChunks(qv, candidates);
            max = ChunkAggregation.MAX.aggregate(hits, k);
            times[r] = (System.nanoTime() - t0) / 1e6;
        }
        Arrays.sort(times);
        return new Run(hits, max, ChunkAggregation.TOP2_MEAN.aggregate(hits, k), times[reps / 2]);
    }

    private static void printTop(String label, List<RankedMovie> top) {
        StringBuilder sb = new StringBuilder(label).append(": ");
        for (int i = 0; i < top.size(); i++) {
            if (i > 0) sb.append(" | ");
            sb.append(i + 1).append(". ").append(top.get(i).title())
              .append(String.format(Locale.ROOT, " (%.3f)", top.get(i).score()));
        }
        System.out.println(sb);
    }

    private static List<String> ids(List<RankedMovie> movies) {
        return movies.stream().map(RankedMovie::id).toList();
    }

    private static int common(List<String> a, List<String> b) {
        Set<String> s = new HashSet<>(a);
        return (int) b.stream().filter(s::contains).count();
    }

    /** Fracción del top-k de referencia que también aparece en el top-k evaluado (sin importar el orden). */
    private static double overlap(List<String> run, List<String> reference) {
        return reference.isEmpty() ? 1.0 : (double) common(run, reference) / reference.size();
    }

    private static Map<String, String> loadQueries(Path file) throws Exception {
        Map<String, String> queries = new LinkedHashMap<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] p = line.split("\t", 2);
            if (p.length == 2) queries.put(p[0], p[1]);
        }
        if (queries.isEmpty()) throw new IllegalArgumentException("No hay consultas en " + file);
        return queries;
    }
}
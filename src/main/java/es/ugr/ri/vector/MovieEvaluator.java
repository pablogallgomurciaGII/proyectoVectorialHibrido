package es.ugr.ri.vector;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Orquestación de evaluación: tabla resumen y exportación por consulta. */
public final class MovieEvaluator {
    public record Summary(String method, int queries, double p, double r, double f1, double map, double ndcg) {}
    public record QueryResult(String queryId, String method, int relevantCount, int retrievedCount, RetrievalMetrics.Scores scores) {}

    @FunctionalInterface public interface Retriever { List<RankedMovie> search(String query) throws Exception; }

    public static Summary evaluate(String method, Retriever retriever, EvaluationData data, int k) throws Exception {
        return evaluate(method, retriever, data, k, null);
    }

    public static Summary evaluate(String method, Retriever retriever, EvaluationData data, int k, List<QueryResult> perQueryOut) throws Exception {
        List<RetrievalMetrics.Scores> all = new ArrayList<>();
        int skipped = 0;
        for (var entry : data.queries.entrySet()) {
            Set<String> relevant = data.relevant.getOrDefault(entry.getKey(), Set.of());
            if (relevant.isEmpty()) { skipped++; continue; }
            List<String> ids = retriever.search(entry.getValue()).stream().limit(k).map(RankedMovie::id).toList();
            RetrievalMetrics.Scores scores = RetrievalMetrics.calculate(ids, relevant, k);
            all.add(scores);
            if (perQueryOut != null) {
                perQueryOut.add(new QueryResult(entry.getKey(), method, relevant.size(), ids.size(), scores));
            }
        }
        if (skipped > 0) System.out.printf("%s: %d consultas omitidas (sin relevantes en el corpus)%n", method, skipped);
        return new Summary(method, all.size(), RetrievalMetrics.mean(all, RetrievalMetrics.Scores::precision),
                RetrievalMetrics.mean(all, RetrievalMetrics.Scores::recall), RetrievalMetrics.mean(all, RetrievalMetrics.Scores::f1),
                RetrievalMetrics.mean(all, RetrievalMetrics.Scores::ap), RetrievalMetrics.mean(all, RetrievalMetrics.Scores::ndcg));
    }

    public static void writeCsv(Path outputPath, List<QueryResult> rows) throws IOException {
        if (outputPath.getParent() != null) Files.createDirectories(outputPath.getParent());
        List<String> lines = new ArrayList<>();
        lines.add("query_id,sistema,relevantes,recuperados,P@k,R@k,F1@k,AP@k,NDCG@k");
        for (QueryResult r : rows) {
            lines.add(String.format(Locale.ROOT, "%s,%s,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f",
                    r.queryId(), r.method(), r.relevantCount(), r.retrievedCount(),
                    r.scores().precision(), r.scores().recall(), r.scores().f1(),
                    r.scores().ap(), r.scores().ndcg()));
        }
        Files.write(outputPath, lines, StandardCharsets.UTF_8);
    }
}
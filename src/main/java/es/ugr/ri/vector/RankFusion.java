package es.ugr.ri.vector;

import java.util.*;

/**
 * Fusión de rankings de PELÍCULAS (nunca de fragmentos). Actividad 3.
 *
 *  - rrf:         RRF(d) = Σ_j 1 / (c + rank_j(d))                       (versión original)
 *  - weightedRrf: RRFα(d) = Σ_j α_j / (c + rank_j(d)),  α_j ≥ 0,  Σ α_j = 1
 *  - linear:      S(d) = α·S̃_BM25(d) + (1 − α)·S̃_vector(d), con normalización min-max
 *
 * rank_j(d) empieza en 1. Un documento ausente de un ranking no recibe aportación de ese ranking.
 * Empates: se resuelven por id de película, para que el orden sea estable y reproducible.
 */
public final class RankFusion {
    private RankFusion() {}

    /** Tolerancia para comprobar que los pesos suman 1 (errores de redondeo de double). */
    private static final double WEIGHT_TOLERANCE = 1e-9;

    /**
     * RRF sin ponderar (todas las listas pesan 1). Se mantiene como caso particular:
     * weightedRrf con α_j = 1/m da EXACTAMENTE el mismo orden, con puntuaciones divididas por m.
     */
    public static List<RankedMovie> rrf(List<RankedMovie> a, List<RankedMovie> b, int c, int k) {
        return fuse(List.of(a, b), new double[] {1.0, 1.0}, c, k);
    }

    /**
     * RRF ponderado para m rankings. alphas[j] es la importancia del ranking j:
     * con dos rankings (BM25, vector), α = (0.7, 0.3) da más peso a BM25.
     */
    public static List<RankedMovie> weightedRrf(List<List<RankedMovie>> rankings, double[] alphas, int c, int k) {
        validateWeights(rankings, alphas);
        return fuse(rankings, alphas, c, k);
    }

    /** Atajo para el caso habitual: BM25 con peso alphaBm25 y vector con peso 1 − alphaBm25. */
    public static List<RankedMovie> weightedRrf(List<RankedMovie> bm25, List<RankedMovie> vector,
                                                double alphaBm25, int c, int k) {
        return weightedRrf(List.of(bm25, vector), new double[] {alphaBm25, 1 - alphaBm25}, c, k);
    }

    /**
     * Fusión lineal con normalización min-max de cada ranking:
     *   S̃(d) = (S(d) − S_min) / (S_max − S_min)    sobre los documentos RECUPERADOS en ese ranking.
     * Decisiones (deben justificarse en el informe):
     *  - Documento ausente de un ranking: S̃ = 0 en ese ranking (igual que el peor recuperado).
     *  - S_max = S_min (un solo documento o todos empatados): S̃ = 1 para todos, porque no hay
     *    información para distinguirlos y todos fueron recuperados.
     */
    public static List<RankedMovie> linear(List<RankedMovie> bm25, List<RankedMovie> vector, double alpha, int k) {
        if (Double.isNaN(alpha) || alpha < 0 || alpha > 1)
            throw new IllegalArgumentException("alpha debe estar en [0, 1]: " + alpha);
        Map<String, Double> nb = minMax(bm25), nv = minMax(vector);
        Map<String, RankedMovie> movies = new HashMap<>();
        for (RankedMovie m : bm25) movies.putIfAbsent(m.id(), m);
        for (RankedMovie m : vector) movies.putIfAbsent(m.id(), m);

        List<RankedMovie> out = new ArrayList<>();
        for (RankedMovie m : movies.values()) {
            double s = alpha * nb.getOrDefault(m.id(), 0.0) + (1 - alpha) * nv.getOrDefault(m.id(), 0.0);
            out.add(new RankedMovie(m.id(), m.title(), s, -1, ""));
        }
        return sortAndCut(out, k);
    }

    /** Puntuación min-max de cada película de un ranking (se usa la primera aparición si hay duplicados). */
    static Map<String, Double> minMax(List<RankedMovie> ranking) {
        Map<String, Double> raw = new LinkedHashMap<>();
        for (RankedMovie m : ranking) raw.putIfAbsent(m.id(), m.score());
        if (raw.isEmpty()) return raw;
        double min = Collections.min(raw.values()), max = Collections.max(raw.values());
        Map<String, Double> out = new HashMap<>();
        for (var e : raw.entrySet())
            out.put(e.getKey(), max == min ? 1.0 : (e.getValue() - min) / (max - min));
        return out;
    }

    // ---------------------------------------------------------------------------------------------

    private static List<RankedMovie> fuse(List<List<RankedMovie>> rankings, double[] weights, int c, int k) {
        if (c < 1) throw new IllegalArgumentException("c debe ser positivo");
        Map<String, Double> scores = new HashMap<>();
        Map<String, RankedMovie> movies = new HashMap<>();
        for (int j = 0; j < rankings.size(); j++) {
            List<RankedMovie> ranked = rankings.get(j);
            Set<String> seen = new HashSet<>();
            int rank = 0;
            for (RankedMovie r : ranked) {
                if (!seen.add(r.id())) continue;          // una película cuenta una vez por ranking
                rank++;
                if (weights[j] == 0) continue;            // peso 0: el ranking no aporta
                scores.merge(r.id(), weights[j] / (c + rank), Double::sum);
                movies.putIfAbsent(r.id(), r);
            }
        }
        List<RankedMovie> out = new ArrayList<>();
        for (var e : scores.entrySet()) {
            RankedMovie m = movies.get(e.getKey());
            out.add(new RankedMovie(m.id(), m.title(), e.getValue(), -1, ""));
        }
        return sortAndCut(out, k);
    }

    private static void validateWeights(List<List<RankedMovie>> rankings, double[] alphas) {
        if (alphas == null || alphas.length != rankings.size())
            throw new IllegalArgumentException("Debe haber un peso por ranking");
        double sum = 0;
        for (double a : alphas) {
            if (Double.isNaN(a) || a < 0) throw new IllegalArgumentException("Los pesos no pueden ser negativos: " + a);
            sum += a;
        }
        if (Math.abs(sum - 1) > WEIGHT_TOLERANCE)
            throw new IllegalArgumentException("Los pesos deben sumar 1 (suman " + sum + ")");
    }

    private static List<RankedMovie> sortAndCut(List<RankedMovie> movies, int k) {
        movies.sort(Comparator.comparingDouble(RankedMovie::score).reversed().thenComparing(RankedMovie::id));
        return List.copyOf(movies.subList(0, Math.max(0, Math.min(k, movies.size()))));
    }
}
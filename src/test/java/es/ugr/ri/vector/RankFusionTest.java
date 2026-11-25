package es.ugr.ri.vector;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Actividad 3: RRF, RRF ponderado y fusión lineal con rankings pequeños calculados a mano.
 *
 * Rankings de ejemplo (c = 60):
 *   L1 (BM25)   = A, B, C      puntuaciones 10, 6, 2
 *   L2 (vector) = B, D, A      puntuaciones 0.9, 0.8, 0.7
 */
class RankFusionTest {

    private static RankedMovie m(String id, double score) { return new RankedMovie(id, "Pelicula " + id, score, -1, ""); }

    private static final List<RankedMovie> L1 = List.of(m("A", 10), m("B", 6), m("C", 2));
    private static final List<RankedMovie> L2 = List.of(m("B", 0.9), m("D", 0.8), m("A", 0.7));
    private static final double EPS = 1e-12;

    private static List<String> ids(List<RankedMovie> r) { return r.stream().map(RankedMovie::id).toList(); }
    private static double score(List<RankedMovie> r, String id) {
        return r.stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow().score();
    }

    // ---------------------------------------------------------------- RRF sin ponderar

    //Estos calculos son hechos con la formula de RRF, y se comprueba que los resultados son correctos
    @Test
    void rrfCalculadoAMano() {
        List<RankedMovie> r = RankFusion.rrf(L1, L2, 60, 10);
        // A: 1/61 + 1/63 = 0.032266   (1.º en L1, 3.º en L2)
        // B: 1/62 + 1/61 = 0.032522   (2.º en L1, 1.º en L2)
        // C: 1/63        = 0.015873   (solo en L1)
        // D: 1/62        = 0.016129   (solo en L2)
        assertEquals(1.0 / 61 + 1.0 / 63, score(r, "A"), EPS);
        assertEquals(1.0 / 62 + 1.0 / 61, score(r, "B"), EPS);
        assertEquals(1.0 / 63, score(r, "C"), EPS);
        assertEquals(1.0 / 62, score(r, "D"), EPS);
        assertEquals(List.of("B", "A", "D", "C"), ids(r));
    }

    @Test
    void rrfDocumentosEnUnSoloRankingQuedanDetrasDeLosQueEstanEnAmbos() {
        List<String> r = ids(RankFusion.rrf(L1, L2, 60, 10));
        // C es 3.º en L1 y D 2.º en L2, pero ambos quedan por detrás de A y B, presentes en los dos.
        assertTrue(r.indexOf("A") < r.indexOf("D") && r.indexOf("B") < r.indexOf("C"));
    }

    @Test
    void rrfEmpateSeResuelvePorId() {
        // X: 1/61 + 1/62 ; Y: 1/62 + 1/61 → empate exacto.
        List<RankedMovie> r = RankFusion.rrf(List.of(m("Y", 5), m("X", 4)), List.of(m("X", .9), m("Y", .8)), 60, 2);
        assertEquals(score(r, "X"), score(r, "Y"), EPS);
        assertEquals(List.of("X", "Y"), ids(r));
    }

    @Test
    void rrfCortaEnKYSinDuplicados() {
        List<RankedMovie> r = RankFusion.rrf(L1, L2, 60, 2);
        assertEquals(List.of("B", "A"), ids(r));
        assertThrows(IllegalArgumentException.class, () -> RankFusion.rrf(L1, L2, 0, 2));
    }

    // ---------------------------------------------------------------- RRF ponderado

    @Test
    void rrfPonderadoCalculadoAMano() {
        List<RankedMovie> r = RankFusion.weightedRrf(L1, L2, 0.8, 60, 10);
        // A: 0.8/61 + 0.2/63 = 0.016290 ; B: 0.8/62 + 0.2/61 = 0.016182
        // C: 0.8/63 = 0.012698          ; D: 0.2/62 = 0.003226
        assertEquals(0.8 / 61 + 0.2 / 63, score(r, "A"), EPS);
        assertEquals(0.8 / 62 + 0.2 / 61, score(r, "B"), EPS);
        assertEquals(List.of("A", "B", "C", "D"), ids(r));   // con más peso a BM25, A supera a B
    }

    @Test
    void rrfSinPonderarEsElCasoParticularDePesosIguales() {
        List<RankedMovie> plain = RankFusion.rrf(L1, L2, 60, 10);
        List<RankedMovie> half = RankFusion.weightedRrf(L1, L2, 0.5, 60, 10);
        assertEquals(ids(plain), ids(half));                         // mismo orden
        for (RankedMovie p : plain) assertEquals(p.score() / 2, score(half, p.id()), EPS);   // puntuación / m
    }

    @Test
    void rrfPonderadoConPesoUnoReproduceUnSoloRanking() {
        assertEquals(List.of("A", "B", "C"), ids(RankFusion.weightedRrf(L1, L2, 1.0, 60, 10)));
        assertEquals(List.of("B", "D", "A"), ids(RankFusion.weightedRrf(L1, L2, 0.0, 60, 10)));
    }

    @Test
    void rrfPonderadoValidaLosPesos() {
        assertThrows(IllegalArgumentException.class,
                () -> RankFusion.weightedRrf(List.of(L1, L2), new double[] {0.7, 0.7}, 60, 5));   // suman 1.4
        assertThrows(IllegalArgumentException.class,
                () -> RankFusion.weightedRrf(List.of(L1, L2), new double[] {1.2, -0.2}, 60, 5));  // negativo
        assertThrows(IllegalArgumentException.class,
                () -> RankFusion.weightedRrf(List.of(L1, L2), new double[] {1.0}, 60, 5));        // falta un peso
    }

    // ---------------------------------------------------------------- Fusión lineal

    @Test
    void minMaxCalculadoAMano() {
        // BM25: (10 − 2)/8 = 1 ; (6 − 2)/8 = 0.5 ; (2 − 2)/8 = 0
        var n = RankFusion.minMax(L1);
        assertEquals(1.0, n.get("A"), EPS);
        assertEquals(0.5, n.get("B"), EPS);
        assertEquals(0.0, n.get("C"), EPS);
    }

    @Test
    void linealCalculadaAMano() {
        List<RankedMovie> r = RankFusion.linear(L1, L2, 0.5, 10);
        // Normalizadas: BM25 A=1, B=0.5, C=0 ; vector B=1, D=0.5, A=0 ; ausente = 0
        // A: 0.5·1   + 0.5·0   = 0.50 ; B: 0.5·0.5 + 0.5·1 = 0.75
        // C: 0.5·0   + 0.5·0   = 0.00 ; D: 0.5·0   + 0.5·0.5 = 0.25
        assertEquals(0.50, score(r, "A"), EPS);
        assertEquals(0.75, score(r, "B"), EPS);
        assertEquals(0.00, score(r, "C"), EPS);
        assertEquals(0.25, score(r, "D"), EPS);
        assertEquals(List.of("B", "A", "D", "C"), ids(r));
    }

    @Test
    void linealSinNormalizarBm25DominariaLaSuma() {
        // Justificación de min-max: sumando puntuaciones en bruto, BM25 (0–10) aplasta al vector (0.7–0.9).
        double rawA = 0.5 * 10 + 0.5 * 0.7, rawB = 0.5 * 6 + 0.5 * 0.9;
        assertTrue(rawA > rawB);                                                   // A gana solo por la escala de BM25
        assertEquals("B", RankFusion.linear(L1, L2, 0.5, 1).get(0).id());          // normalizado, gana B
    }

    @Test
    void linealPeorRecuperadoEquivaleAAusente() {
        // Limitación de min-max: C (3.º en BM25) normaliza a 0, igual que si no se hubiera recuperado.
        List<RankedMovie> r = RankFusion.linear(L1, L2, 1.0, 10);
        assertEquals(0.0, score(r, "C"), EPS);
        assertEquals(0.0, score(r, "D"), EPS);   // D no está en BM25
    }

    @Test
    void linealRankingDeUnSoloDocumentoYValidacion() {
        // S_max = S_min → S̃ = 1
        assertEquals(1.0, RankFusion.linear(List.of(m("A", 3)), List.of(), 1.0, 5).get(0).score(), EPS);
        assertThrows(IllegalArgumentException.class, () -> RankFusion.linear(L1, L2, 1.5, 5));
        assertThrows(IllegalArgumentException.class, () -> RankFusion.linear(L1, L2, -0.1, 5));
    }
}
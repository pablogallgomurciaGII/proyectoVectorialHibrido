package es.ugr.ri.vector;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Actividad 2: agregación de fragmentos en películas, sin Lucene ni modelo. */
class ChunkAggregationTest {

    private static ChunkHit hit(int rank, String movie, int chunk, double score) {
        return new ChunkHit(rank, movie, "Pelicula " + movie, chunk, score, "texto " + movie + chunk);
    }

    /** 5 fragmentos candidatos, pero solo 3 películas: A ocupa las posiciones 1, 3 y 5. */
    private static final List<ChunkHit> HITS = List.of(
            hit(1, "A", 0, 0.90),
            hit(2, "B", 4, 0.80),
            hit(3, "A", 2, 0.70),
            hit(4, "C", 1, 0.60),
            hit(5, "A", 7, 0.50));

    @Test
    void kFragmentosNoSonKPeliculas() {
        assertEquals(5, HITS.size());
        assertEquals(3, ChunkAggregation.distinctMovies(HITS));
        // Aunque se pidan 5 películas, solo hay 3 distintas entre los candidatos.
        assertEquals(3, ChunkAggregation.MAX.aggregate(HITS, 5).size());
    }

    @Test
    void resultadoSinDuplicadosYOrdenado() {
        for (ChunkAggregation agg : ChunkAggregation.values()) {
            List<RankedMovie> r = agg.aggregate(HITS, 10);
            assertEquals(r.size(), new HashSet<>(r.stream().map(RankedMovie::id).toList()).size(), agg + ": duplicados");
            for (int i = 1; i < r.size(); i++)
                assertTrue(r.get(i - 1).score() >= r.get(i).score(), agg + ": no ordenado");
        }
    }

    @Test
    void maxUsaElMejorFragmentoYLoConservaComoEvidencia() {
        List<RankedMovie> r = ChunkAggregation.MAX.aggregate(HITS, 3);
        assertEquals(List.of("A", "B", "C"), r.stream().map(RankedMovie::id).toList());
        assertEquals(0.90, r.get(0).score(), 1e-12);
        assertEquals(0, r.get(0).chunkId());          // fragmento 0 de A, el de 0.90
        assertEquals("texto A0", r.get(0).excerpt());
    }

    @Test
    void top2PromediaLosDosMejores() {
        List<RankedMovie> r = ChunkAggregation.TOP2_MEAN.aggregate(HITS, 3);
        // Último candidato recuperado: 0.50 (A, fragmento 7).
        // A: (0.90 + 0.70) / 2 = 0.80 ; B: (0.80 + 0.50) / 2 = 0.65 ; C: (0.60 + 0.50) / 2 = 0.55
        assertEquals(List.of("A", "B", "C"), r.stream().map(RankedMovie::id).toList());
        assertEquals(0.80, score(r, "A"), 1e-12);
        assertEquals(0.65, score(r, "B"), 1e-12);
        assertEquals(0.55, score(r, "C"), 1e-12);
        // Evidencia: sigue siendo el mejor fragmento, no el segundo.
        assertEquals(0, r.get(0).chunkId());
    }

    @Test
    void top2ConUnSoloFragmentoUsaElUltimoCandidatoComoSegundo() {
        // Único candidato: el último candidato es él mismo, así que la media es su puntuación.
        assertEquals(0.75, ChunkAggregation.TOP2_MEAN.aggregate(List.of(hit(1, "X", 3, 0.75)), 5).get(0).score(), 1e-12);
    }

    @Test
    void top2NoFavoreceAPeliculasConUnSoloFragmento() {
        // A tiene dos fragmentos buenos; B uno solo, algo mejor que la media de A.
        List<ChunkHit> hits = List.of(hit(1, "A", 0, 0.90), hit(2, "B", 0, 0.89),
                                      hit(3, "A", 1, 0.86), hit(4, "C", 0, 0.40));
        // MAX: A (0.90) > B (0.89). Con la política antigua B (0.89) ganaba a A (0.88).
        // Ahora: A = 0.88 ; B = (0.89 + 0.40) / 2 = 0.645 → A primero.
        List<RankedMovie> r = ChunkAggregation.TOP2_MEAN.aggregate(hits, 3);
        assertEquals("A", r.get(0).id());
        assertEquals(0.645, score(r, "B"), 1e-12);
    }

    @Test
    void top2PuedeCambiarElOrdenRespectoAMax() {
        // A: un fragmento excelente y otro malo. B: dos fragmentos buenos.
        List<ChunkHit> hits = List.of(hit(1, "A", 0, 0.90), hit(2, "B", 0, 0.85),
                                      hit(3, "B", 1, 0.80), hit(4, "A", 1, 0.30));
        assertEquals("A", ChunkAggregation.MAX.aggregate(hits, 2).get(0).id());        // 0.90 > 0.85
        assertEquals("B", ChunkAggregation.TOP2_MEAN.aggregate(hits, 2).get(0).id());  // 0.825 > 0.60 (A: 0.90 y 0.30)
    }

    @Test
    void empateSeResuelvePorIdDePelicula() {
        // Valores exactos en binario para que el empate sea exacto.
        List<ChunkHit> hits = List.of(hit(1, "B", 0, 0.75), hit(2, "A", 0, 0.75));
        assertEquals(List.of("A", "B"),
                ChunkAggregation.MAX.aggregate(hits, 2).stream().map(RankedMovie::id).toList());
    }

    @Test
    void corteEnKYCasosVacios() {
        assertEquals(2, ChunkAggregation.MAX.aggregate(HITS, 2).size());
        assertTrue(ChunkAggregation.MAX.aggregate(HITS, 0).isEmpty());
        assertTrue(ChunkAggregation.TOP2_MEAN.aggregate(List.of(), 5).isEmpty());
    }

    @Test
    void parseDeLaPropiedad() {
        assertEquals(ChunkAggregation.MAX, ChunkAggregation.parse("max"));
        assertEquals(ChunkAggregation.TOP2_MEAN, ChunkAggregation.parse("TOP2"));
        assertThrows(IllegalArgumentException.class, () -> ChunkAggregation.parse("media"));
    }

    private static double score(List<RankedMovie> r, String id) {
        return r.stream().filter(m -> m.id().equals(id)).findFirst().orElseThrow().score();
    }
}
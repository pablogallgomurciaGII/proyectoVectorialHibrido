package es.ugr.ri.vector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class TextChunkerTest {

    private static final int W = 10;

    /** Sinopsis de n palabras distintas: "p0 p1 p2 ...". */
    private static String plot(int n) {
        return IntStream.range(0, n).mapToObj(i -> "p" + i).collect(Collectors.joining(" "));
    }

    private static List<MovieChunk> trocear(int w, int o, int n) {
        return new TextChunker(w, o).split(new Movie("1", "Titulo", plot(n)));
    }

    private static int palabras(MovieChunk c) {
        return c.text().split("\\s+").length;
    }

    /** Reconstruye la sinopsis quitando las palabras solapadas de cada fragmento. */
    private static String reconstruir(List<MovieChunk> chunks, int o) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            String[] w = chunks.get(i).text().split("\\s+");
            for (int j = (i == 0 ? 0 : o); j < w.length; j++) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(w[j]);
            }
        }
        return sb.toString();
    }

    @Test
    void sinopsisVaciaONula() {
        assertEquals(0, trocear(W, 3, 0).size());
        assertEquals(0, new TextChunker(W, 3).split(new Movie("1", "T", null)).size());
        assertEquals(0, new TextChunker(W, 3).split(new Movie("1", "T", "   ")).size());
    }

    @Test
    void menosDeWPalabras() {
        List<MovieChunk> c = trocear(W, 3, W - 1);
        assertEquals(1, c.size());
        assertEquals(W - 1, palabras(c.get(0)));
    }

    @Test
    void exactamenteWPalabras() {
        assertEquals(1, trocear(W, 3, W).size());
    }

    @Test
    void wMasUnaPalabra() {
        List<MovieChunk> c = trocear(W, 3, W + 1);
        assertEquals(2, c.size());
        assertEquals("p7 p8 p9 p10", c.get(1).text()); // el último fragmento corto conserva el solapamiento
    }

    @Test
    void dosWMasUna() {
        assertEquals(3, trocear(W, 3, 2 * W + 1).size());
        assertEquals(3, trocear(W, 0, 2 * W + 1).size()); // 10 + 10 + 1 sin solapamiento
    }

    @Test
    void cobertura_y_solapamiento_ejemplo_controlado() {
        List<MovieChunk> c = trocear(W, 3, 21);
        assertEquals("p0 p1 p2 p3 p4 p5 p6 p7 p8 p9", c.get(0).text());
        assertEquals("p7 p8 p9 p10 p11 p12 p13 p14 p15 p16", c.get(1).text());
        assertEquals("p14 p15 p16 p17 p18 p19 p20", c.get(2).text());
    }

    @Test
    void todasLasPalabrasApareceEnOrden() {
        for (int o : new int[] {0, 3, W - 1}) {
            for (int n : new int[] {1, W - 1, W, W + 1, 2 * W + 1, 57}) {
                List<MovieChunk> c = trocear(W, o, n);
                assertEquals(plot(n), reconstruir(c, o), "o=" + o + " n=" + n);
                assertTrue(c.stream().allMatch(x -> palabras(x) <= W));
            }
        }
    }

    @Test
    void chunkIdConsecutivosDesdeCero() {
        List<MovieChunk> c = trocear(W, 3, 57);
        for (int i = 0; i < c.size(); i++) {
            assertEquals(i, c.get(i).chunkId());
            assertEquals("1", c.get(i).movieId());
        }
    }

    @Test
    void parametrosInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> new TextChunker(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new TextChunker(W, -1));
        assertThrows(IllegalArgumentException.class, () -> new TextChunker(W, W));
        assertThrows(IllegalArgumentException.class, () -> new TextChunker(W, W + 5));
    }
}
package es.ugr.ri.vector;
import java.util.*;
/** EJERCICIO: implementación intencionadamente incompleta.
 * Solo conserva las primeras k palabras: pierde el resto del argumento.
 * Sustituir por una segmentación completa con solapamiento y, si es posible, por oraciones.
 */
public final class TextChunker {
    private final int k;
    public TextChunker(int k) { if (k < 1) throw new IllegalArgumentException("k debe ser positivo"); this.k = k; }
    public List<MovieChunk> split(Movie movie) {
        if (movie.plot() == null || movie.plot().isBlank()) return List.of();
        String[] words = movie.plot().trim().split("\\s+");
        String first = String.join(" ", Arrays.copyOfRange(words, 0, Math.min(k, words.length)));
        return List.of(new MovieChunk(movie.id(), movie.title(), 0, first));
    }
}

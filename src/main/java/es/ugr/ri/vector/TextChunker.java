package es.ugr.ri.vector;
import java.util.*;
/** EJERCICIO: implementación intencionadamente incompleta.
 * Solo conserva las primeras k palabras: pierde el resto del argumento.
 * Sustituir por una segmentación completa con solapamiento y, si es posible, por oraciones.
 */
public final class TextChunker {
    /*
    private final int k;
    public TextChunker(int k) { if (k < 1) throw new IllegalArgumentException("k debe ser positivo"); this.k = k; }
    public List<MovieChunk> split(Movie movie) {
        if (movie.plot() == null || movie.plot().isBlank()) return List.of();
        String[] words = movie.plot().trim().split("\\s+");
        String first = String.join(" ", Arrays.copyOfRange(words, 0, Math.min(k, words.length)));
        return List.of(new MovieChunk(movie.id(), movie.title(), 0, first));
    }
         */

    private final int words;
    private final int overlap;

    public TextChunker(int words) {
       this(words, 0);
    }

    public TextChunker(int words, int overlap){
        if (words < 1)
        {
            throw new IllegalArgumentException("El tamaño del fragmento debe ser positivo");
        }
        // Quitar este if provoca fallo en el test parametrosInvalidos
        if (overlap < 0 || overlap >= words)
        {
            throw new IllegalArgumentException("El solapamiento no puede ser negativo ni mayor o igual que el tamaño del fragmento, debe cumplir con 0<= o < w");
        }
        this.words = words;
        this.overlap = overlap;
    }

    public List<MovieChunk> split(Movie movie) {
        if (movie.plot() == null || movie.plot().isBlank()) return List.of();
        String[] w = movie.plot().trim().split("\\s+");
        // int step = words; // Debería fallar tests: cobertura_y_solapamiento…, wMasUnaPalabra
        int step = words - overlap;

        List<MovieChunk> chunks = new ArrayList<>();
        int start = 0;
        int id = 0;
        while (true)
        {
            // int end = start + words - 1; // Debería fallar tests: todasLasPalabras…, exactamenteWPalabras
            int end = Math.min(start + words, w.length);
            String text = String.join(" ", Arrays.copyOfRange(w, start, end));
            chunks.add(new MovieChunk(movie.id(), movie.title(), id++, text)); // cambiar id++ → id (todos con id 0) provoca fallo del test chunkIdConsecutivosDesdeCero
            if (end == w.length) break;
            start += step;
        }
        return chunks;
    }
}

package es.ugr.ri.vector;

import java.util.Arrays;
import java.util.List;

/**
 * Comprobación obligatoria de la Actividad 1: antes de indexar, muestra para
 * algunas películas el número de fragmentos, su longitud y sus primeras
 * palabras, y comprueba que el final de la sinopsis no se pierde.
 *
 * Uso: InspeccionFragmentos [sample|ruta.jsonl] [texto_en_titulo ...]
 * Parámetros: -Dchunk.words=120 -Dchunk.overlap=20 -Dlimit=N
 */
public final class InspeccionFragmentos {

    // Número de palabras a mostrar de cada fragmento
    private static final int PALABRAS_MUESTRA = 8;

    //Aquí se hace la inspección de los fragmentos de las películas, mostrando el número de fragmentos, su longitud y sus primeras palabras, y comprobando que el final de la sinopsis no se pierde.
    public static void main(String[] args) throws Exception {
        String origen = args.length > 0 ? args[0] : "sample";
        List<String> filtros = args.length > 1
                ? Arrays.asList(args).subList(1, args.length) : List.of();

        RunConfig cfg = RunConfig.fromProperties();
        List<Movie> movies = MovieRepository.load(origen, cfg.limit());
        TextChunker chunker = new TextChunker(cfg.chunkWords(), cfg.chunkOverlap());

        System.out.printf("Configuración: w=%d, o=%d | películas cargadas: %d%n%n",
                cfg.chunkWords(), cfg.chunkOverlap(), movies.size());

        int mostradas = 0;
        for (Movie m : movies) {
            boolean seleccionada = filtros.isEmpty()
                    ? mostradas < 5
                    : filtros.stream().anyMatch(f -> m.title().toLowerCase().contains(f.toLowerCase()));
            if (!seleccionada) {
                continue;
            }
            mostradas++;
            inspeccionar(m, chunker);
        }

        System.out.println("=== Fragmentos totales según configuración (sin calcular embeddings) ===");
        System.out.printf("%-10s %-10s %-12s %-14s%n", "w", "o", "fragmentos", "media/película");
        for (int[] c : new int[][] {{100, 0}, {100, 25}, {150, 0}, {150, 25}}) {
            TextChunker t = new TextChunker(c[0], c[1]);
            int total = 0;
            for (Movie m : movies) {
                total += t.split(m).size();
            }
            System.out.printf("%-10d %-10d %-12d %-14.2f%n", c[0], c[1], total,
                    movies.isEmpty() ? 0.0 : (double) total / movies.size());
        }
    }

    // Muestra información de los fragmentos de una película y comprueba que el final de la sinopsis se conserva.
    private static void inspeccionar(Movie m, TextChunker chunker) {
        String[] palabras = m.plot().trim().split("\\s+");
        List<MovieChunk> chunks = chunker.split(m);
        System.out.printf("%s (id %s): %d palabras, %d fragmentos%n",
                m.title(), m.id(), palabras.length, chunks.size());

        for (MovieChunk c : chunks) {
            String[] w = c.text().split("\\s+");
            String inicio = String.join(" ",
                    Arrays.copyOfRange(w, 0, Math.min(PALABRAS_MUESTRA, w.length)));
            System.out.printf("  [%d] %d palabras | %s ...%n", c.chunkId(), w.length, inicio);
        }

        // La última palabra de la sinopsis debe estar al final del último fragmento
        if (!chunks.isEmpty()) {
            String ultimaSinopsis = palabras[palabras.length - 1];
            String[] wUlt = chunks.get(chunks.size() - 1).text().split("\\s+");
            String ultimaFragmento = wUlt[wUlt.length - 1];
            System.out.println("  Final de la sinopsis conservado: "
                    + (ultimaSinopsis.equals(ultimaFragmento) ? "SÍ" : "NO (revisar)"));
        }
        System.out.println();
    }
}
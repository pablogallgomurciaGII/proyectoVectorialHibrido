package es.ugr.ri.vector;

import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Properties;
import org.apache.lucene.util.Version;

/**
 * Indexador: argumento 1 = JSONL o sample; argumento 2 = directorio destino.
 * Parámetros: -Dchunk.words=120 -Dchunk.overlap=20 -Dlimit=N
 * Junto a los índices escribe index_info.properties con la configuración usada (apartado 4.6).
 */
public final class MovieIndexerMain {
    static final String MODEL = "sentence-transformers/all-MiniLM-L6-v2 (LangChain4j ONNX)";
    static final String SIMILARITY = "DOT_PRODUCT sobre vectores L2-normalizados (equivale a coseno)";

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Uso: MovieIndexerMain <sample|peliculas.jsonl> <directorio-indices>");
        }
        RunConfig config = RunConfig.fromProperties();
        Path root = Path.of(args[1]);
        var movies = MovieRepository.load(args[0], config.limit());
        System.out.printf("Indexando %d películas en %s con w=%d, o=%d%n",
                movies.size(), root, config.chunkWords(), config.chunkOverlap());

        long inicio = System.nanoTime();
        MovieIndexer.Stats stats;
        try (IndexDirectories indexes = IndexDirectories.disk(root)) {
            stats = new MovieIndexer().build(movies, indexes,
                    new TextChunker(config.chunkWords(), config.chunkOverlap()), new EmbeddingService());
        }
        double segundos = (System.nanoTime() - inicio) / 1e9;

        System.out.printf(Locale.ROOT, "Índices persistentes en %s%n", root);
        System.out.printf(Locale.ROOT, "  películas=%d  fragmentos=%d  fragmentos/película=%.2f  dimensión=%d  omitidas=%d%n",
                stats.movies(), stats.chunks(),
                stats.movies() == 0 ? 0.0 : (double) stats.chunks() / stats.movies(),
                stats.dimension(), stats.skipped());
        System.out.printf(Locale.ROOT, "  tiempo de indexación=%.1f s%n", segundos);

        writeInfo(root, args[0], config, stats, segundos);
    }

    /** Guarda junto al índice los datos necesarios para reproducirlo y explicar sus resultados. */
    private static void writeInfo(Path root, String corpus, RunConfig config,
                                  MovieIndexer.Stats stats, double segundos) throws Exception {
        Properties p = new Properties();
        p.setProperty("corpus", corpus);
        p.setProperty("limit", config.limit() == Integer.MAX_VALUE ? "sin límite" : String.valueOf(config.limit()));
        p.setProperty("chunk.words", String.valueOf(config.chunkWords()));
        p.setProperty("chunk.overlap", String.valueOf(config.chunkOverlap()));
        p.setProperty("model", MODEL);
        p.setProperty("dimension", String.valueOf(stats.dimension()));
        p.setProperty("similarity", SIMILARITY);
        p.setProperty("lucene.version", Version.LATEST.toString());
        p.setProperty("movies", String.valueOf(stats.movies()));
        p.setProperty("chunks", String.valueOf(stats.chunks()));
        p.setProperty("skipped", String.valueOf(stats.skipped()));
        p.setProperty("indexing.seconds", String.format(Locale.ROOT, "%.1f", segundos));
        p.setProperty("created", LocalDateTime.now().toString());
        Path file = root.resolve("index_info.properties");
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            p.store(w, "Configuración con la que se construyeron estos índices");
        }
        System.out.println("  configuración guardada en " + file);
    }
}
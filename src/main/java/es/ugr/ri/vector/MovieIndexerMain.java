package es.ugr.ri.vector;
import java.nio.file.Path;
/** Indexador: argumento 1 = JSONL o sample; argumento 2 = directorio destino. */
public final class MovieIndexerMain {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Uso: MovieIndexerMain <sample|peliculas.jsonl> <directorio-indices>");
        }
        RunConfig config = RunConfig.fromProperties();
        var movies = MovieRepository.load(args[0], config.limit());
        try (IndexDirectories indexes = IndexDirectories.disk(Path.of(args[1]))) {
            var stats = new MovieIndexer().build(movies, indexes,
                    new TextChunker(config.chunkWords()), new EmbeddingService());
            System.out.printf("Índices persistentes en %s: %d películas y %d fragmentos%n",
                    args[1], stats.movies(), stats.chunks());
        }
    }
}

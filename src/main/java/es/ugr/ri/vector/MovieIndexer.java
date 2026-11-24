package es.ugr.ri.vector;
import java.io.IOException;
import java.util.*;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.util.VectorUtil;
/** Indexación separada de la búsqueda; índices RAM compartidos por el orquestador. */
public final class MovieIndexer {
    /** movies: leídas; chunks: fragmentos indexados; dimension: componentes de cada vector;
     *  skipped: películas sin vector global (sinopsis vacía o media nula). */
    public record Stats(int movies, int chunks, int dimension, int skipped) {}
    private static final int PROGRESS_EVERY = 1000;
    public Stats build(List<Movie> movies, IndexDirectories dirs, TextChunker chunker, EmbeddingService embeddings) throws IOException {
        int total = 0, dimension = 0, skipped = 0, processed = 0;
        try (IndexWriter lexical = new IndexWriter(dirs.lexical, new IndexWriterConfig(new StandardAnalyzer()).setOpenMode(IndexWriterConfig.OpenMode.CREATE));
             IndexWriter fragments = new IndexWriter(dirs.chunks, new IndexWriterConfig().setOpenMode(IndexWriterConfig.OpenMode.CREATE));
             IndexWriter global = new IndexWriter(dirs.global, new IndexWriterConfig().setOpenMode(IndexWriterConfig.OpenMode.CREATE))) {
            for (Movie movie : movies) {
                Document ld = new Document();
                ld.add(new StringField("movie_id", movie.id(), Field.Store.YES));
                ld.add(new StoredField("title", movie.title()));
                ld.add(new TextField("title_text", movie.title(), Field.Store.NO));
                ld.add(new TextField("plot", movie.plot(), Field.Store.NO));
                lexical.addDocument(ld);
                if (++processed % PROGRESS_EVERY == 0)
                    System.err.printf("  ... %d/%d películas, %d fragmentos%n", processed, movies.size(), total);
                List<MovieChunk> chunks = chunker.split(movie);
                if (chunks.isEmpty()) { skipped++; continue; }
                float[] sum = null;
                for (MovieChunk c : chunks) {
                    float[] v = embeddings.embed(c.text());
                    if (sum == null) sum = new float[v.length];
                    dimension = v.length;
                    VectorUtil.add(sum, v);
                    Document d = new Document();
                    d.add(new StringField("movie_id", c.movieId(), Field.Store.YES));
                    d.add(new StoredField("title", c.title()));
                    d.add(new StoredField("chunk_id", c.chunkId()));
                    d.add(new StoredField("chunk_text", c.text()));
                    d.add(new KnnFloatVectorField("vector", v, VectorSimilarityFunction.DOT_PRODUCT));
                    fragments.addDocument(d);
                    total++;
                }
                // Apartado 4.4: la media solo se normaliza si su norma no es cero
                // (p. ej. fragmentos con vectores opuestos). Sin vector válido no hay documento global.
                if (VectorUtil.dotProduct(sum, sum) == 0f) {
                    System.err.printf("AVISO: vector medio nulo para %s (%s); se omite del índice global%n", movie.id(), movie.title());
                    skipped++;
                    continue;
                }
                VectorUtil.l2normalize(sum);
                Document gd = new Document();
                gd.add(new StringField("movie_id", movie.id(), Field.Store.YES));
                gd.add(new StoredField("title", movie.title()));
                gd.add(new KnnFloatVectorField("vector", sum, VectorSimilarityFunction.DOT_PRODUCT));
                global.addDocument(gd);
            }
        }
        return new Stats(movies.size(), total, dimension, skipped);
    }
}
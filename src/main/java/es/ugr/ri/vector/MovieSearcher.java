package es.ugr.ri.vector;
import java.io.IOException;
import java.util.*;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
/** Recuperador independiente: BM25, vectores y fusión. */
public final class MovieSearcher implements AutoCloseable {
    private final DirectoryReader lexicalReader, chunkReader, globalReader;
    private final IndexSearcher lexical, chunks, global;
    private final EmbeddingService embeddings;
    public MovieSearcher(IndexDirectories indexes, EmbeddingService embeddings) throws IOException {
        this.embeddings = embeddings;
        lexicalReader = DirectoryReader.open(indexes.lexical);
        chunkReader = DirectoryReader.open(indexes.chunks);
        globalReader = DirectoryReader.open(indexes.global);
        lexical = new IndexSearcher(lexicalReader);
        chunks = new IndexSearcher(chunkReader);
        global = new IndexSearcher(globalReader);
    }
    public List<RankedMovie> bm25(String query, int k) throws Exception {
        var parser = new org.apache.lucene.queryparser.classic.MultiFieldQueryParser(new String[]{"title_text", "plot"}, new StandardAnalyzer());
        Query q = parser.parse(QueryParser.escape(query));
        var docs = lexical.search(q, k);
        List<RankedMovie> out = new ArrayList<>();
        for (var hit : docs.scoreDocs) {
            var d = lexical.storedFields().document(hit.doc);
            out.add(new RankedMovie(d.get("movie_id"), d.get("title"), hit.score, -1, ""));
        }
        return out;
    }
    /**
     * Ranking de PELÍCULAS a partir de fragmentos, con la agregación de referencia (MAX).
     * Se mantiene la firma original para no romper a quien ya la usa (evaluador, RRF, demo).
     */
    public List<RankedMovie> vectorChunks(String query, int candidates, int k) throws IOException {
        return vectorChunks(query, candidates, k, ChunkAggregation.MAX);
    }

    /** Actividad 2: igual que el anterior pero eligiendo la agregación (MAX o TOP2_MEAN). */
    public List<RankedMovie> vectorChunks(String query, int candidates, int k, ChunkAggregation aggregation) throws IOException {
        return aggregation.aggregate(rawChunks(query, candidates), k);
    }

    /**
     * Ranking BRUTO de fragmentos, antes de agrupar: puede contener varios fragmentos de la
     * misma película. Sirve para demostrar que k fragmentos no son k películas distintas.
     */
    public List<ChunkHit> rawChunks(String query, int candidates) throws IOException {
        return searchChunks(embeddings.embed(query), candidates);
    }

    /**
     * kNN sobre el índice de fragmentos con un vector de consulta ya calculado
     * (permite medir el tiempo de la búsqueda sin el coste de calcular el embedding).
     * Si candidates supera el número de fragmentos del índice, se limita a ese número.
     */
    public List<ChunkHit> searchChunks(float[] queryVector, int candidates) throws IOException {
        int n = Math.min(candidates, chunkReader.numDocs());
        if (n <= 0) return List.of();
        var docs = chunks.search(new KnnFloatVectorQuery("vector", queryVector, n), n);
        var stored = chunks.storedFields();
        List<ChunkHit> out = new ArrayList<>(docs.scoreDocs.length);
        int rank = 1;
        for (var hit : docs.scoreDocs) {
            var d = stored.document(hit.doc);
            out.add(new ChunkHit(rank++, d.get("movie_id"), d.get("title"),
                    d.getField("chunk_id").numericValue().intValue(), hit.score, d.get("chunk_text")));
        }
        return out;
    }

    /** Vector de la consulta (mismo modelo y normalización que los documentos). */
    public float[] embedQuery(String query) { return embeddings.embed(query); }

    /** Número de fragmentos en el índice: límite superior real de candidates. */
    public int chunkCount() { return chunkReader.numDocs(); }

    public List<RankedMovie> vectorGlobal(String query, int k) throws IOException {
        int n = Math.min(k, globalReader.numDocs());
        if (n == 0) return List.of();
        var docs = global.search(new KnnFloatVectorQuery("vector", embeddings.embed(query), n), n);
        List<RankedMovie> out = new ArrayList<>();
        for (var hit : docs.scoreDocs) {
            var d = global.storedFields().document(hit.doc);
            out.add(new RankedMovie(d.get("movie_id"), d.get("title"), hit.score, -1, ""));
        }
        return out;
    }
    /** RRF sin ponderar con la configuración original (c = 60, 20 películas de cada ranking). */
    public List<RankedMovie> hybridRrf(String query, int candidates, int k) throws Exception {
        return hybridRrf(query, candidates, k, FusionConfig.defaults());
    }

    /** RRF sin ponderar: Σ 1 / (c + rank). */
    public List<RankedMovie> hybridRrf(String query, int candidates, int k, FusionConfig f) throws Exception {
        int depth = Math.max(k, f.depth());
        return RankFusion.rrf(bm25(query, depth), vectorChunks(query, candidates, depth), f.c(), k);
    }

    /** RRF ponderado: α / (c + rank_BM25) + (1 − α) / (c + rank_vector). */
    public List<RankedMovie> hybridWeightedRrf(String query, int candidates, int k, FusionConfig f) throws Exception {
        int depth = Math.max(k, f.depth());
        return RankFusion.weightedRrf(bm25(query, depth), vectorChunks(query, candidates, depth), f.rrfAlpha(), f.c(), k);
    }

    /** Fusión lineal con min-max: α · BM25 normalizado + (1 − α) · vector normalizado. */
    public List<RankedMovie> hybridLinear(String query, int candidates, int k, FusionConfig f) throws Exception {
        int depth = Math.max(k, f.depth());
        return RankFusion.linear(bm25(query, depth), vectorChunks(query, candidates, depth), f.linearAlpha(), k);
    }

    @Override public void close() throws IOException { lexicalReader.close(); chunkReader.close(); globalReader.close(); }
}
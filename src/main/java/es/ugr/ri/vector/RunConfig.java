package es.ugr.ri.vector;

public record RunConfig(String corpus, int limit, int chunkWords, int chunkOverlap, int k, int candidateChunks) {

    /** Constructor anterior (sin solapamiento explícito): usa el valor por defecto. */
    public RunConfig(String corpus, int limit, int chunkWords, int k, int candidateChunks) {
        this(corpus, limit, chunkWords, defaultOverlap(chunkWords), k, candidateChunks);
    }

    /** 20 palabras de solapamiento (sugerencia del guión), siempre menor que el tamaño del fragmento. */
    private static int defaultOverlap(int words) {
        return Math.max(0, Math.min(20, words - 1));
    }

    public static RunConfig fromProperties() {
        int words = Integer.getInteger("chunk.words", 120);
        return new RunConfig(System.getProperty("corpus", "sample"), Integer.getInteger("limit", Integer.MAX_VALUE),
                words, Integer.getInteger("chunk.overlap", defaultOverlap(words)),
                Integer.getInteger("k", 10), Integer.getInteger("candidates", 100));
    }
}
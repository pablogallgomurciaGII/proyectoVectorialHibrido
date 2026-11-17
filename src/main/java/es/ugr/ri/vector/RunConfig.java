package es.ugr.ri.vector;
public record RunConfig(String corpus, int limit, int chunkWords, int k, int candidateChunks) {
    public static RunConfig fromProperties() {
        return new RunConfig(System.getProperty("corpus", "sample"), Integer.getInteger("limit", Integer.MAX_VALUE),
                Integer.getInteger("chunk.words", 120), Integer.getInteger("k", 10), Integer.getInteger("candidates", 100));
    }
}

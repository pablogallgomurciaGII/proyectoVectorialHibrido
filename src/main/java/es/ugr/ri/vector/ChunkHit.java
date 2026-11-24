package es.ugr.ri.vector;

/**
 * Un fragmento recuperado por kNN, ANTES de agrupar por película.
 * rank empieza en 1 y sigue el orden devuelto por Lucene (puntuación descendente).
 * score es la puntuación de Lucene para DOT_PRODUCT: (1 + coseno) / 2.
 */
public record ChunkHit(int rank, String movieId, String title, int chunkId, double score, String text) {}
package es.ugr.ri.vector;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import org.apache.lucene.util.VectorUtil;

import java.util.Locale;

/**
 * Actividad guiada (apartado 3.4): embeddings de dos descripciones próximas y una de tema distinto.
 * Muestra dimensión, norma antes/después de normalizar y la matriz de similitudes coseno.
 *
 * Uso:
 *   mvn -q compile exec:java -Dexec.mainClass=es.ugr.ri.vector.EmbeddingSimilarityDemo
 *   mvn -q compile exec:java -Dexec.mainClass=es.ugr.ri.vector.EmbeddingSimilarityDemo \
 *       -Dexec.args="'texto A' 'texto B' 'texto C'"
 */
public final class EmbeddingSimilarityDemo {

    public static void main(String[] args) {
        String[] texts = args.length >= 2 ? args : new String[] {
                "A detective pursues artificial humans",          // T1
                "A police officer hunts androids",                // T2: misma idea, otras palabras
                "A young girl bakes a chocolate cake for her grandmother", // T3: tema distinto
                "A detective does not pursue artificial humans"  // T4: negación de T1 (caso trampa)
        };

        // Se usa el modelo directamente (no EmbeddingService) para ver la norma ANTES de normalizar.
        EmbeddingModel model = new AllMiniLmL6V2EmbeddingModel();
        float[][] vectors = new float[texts.length][];

        System.out.println("=== Embeddings ===");
        for (int i = 0; i < texts.length; i++) {
            float[] v = model.embed(texts[i]).content().vector();
            double rawNorm = norm(v);
            VectorUtil.l2normalize(v);   // en este modelo ya sale ~1, pero lo garantizamos
            vectors[i] = v;
            System.out.printf(Locale.ROOT, "T%d \"%s\"%n    dimensión=%d  norma original=%.6f  norma tras l2normalize=%.6f%n    primeras componentes=[%.4f, %.4f, %.4f, ...]%n",
                    i + 1, texts[i], v.length, rawNorm, norm(v), v[0], v[1], v[2]);
        }

        System.out.println("\n=== Similitud coseno (producto escalar de vectores unitarios) ===");
        System.out.print("      ");
        for (int j = 0; j < texts.length; j++) System.out.printf("     T%d", j + 1);
        System.out.println();
        for (int i = 0; i < texts.length; i++) {
            System.out.printf("T%d    ", i + 1);
            for (int j = 0; j < texts.length; j++) {
                System.out.printf(Locale.ROOT, " %6.3f", VectorUtil.dotProduct(vectors[i], vectors[j]));
            }
            System.out.println();
        }
    }

    private static double norm(float[] v) {
        double s = 0;
        for (float x : v) s += (double) x * x;
        return Math.sqrt(s);
    }
}
package es.ugr.ri.vector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.*;
import org.junit.jupiter.api.Test;

public final class MetricsSmokeTest {

    public static void main(String[] args) {
        MetricsSmokeTest t = new MetricsSmokeTest();
        t.relevanteEnPrimeraPosicion();
        t.relevanteEnUltimaPosicion();
        t.variosRelevantesEjemploGuion();
        t.sinRelevantesRecuperados();
        t.masRelevantesQueK();
        t.sinDocumentosRelevantesEnQrels();
        System.out.println("OK: todos los casos de métricas verificados");
    }

    //Test que prueba si es relevante en la primera posición, se calcula correctamente precision, recall, f1, ap y ndcg
    @Test
    void relevanteEnPrimeraPosicion() {
        var s = RetrievalMetrics.calculate(List.of("R1", "X1", "X2", "X3", "X4"), Set.of("R1"), 5);
        assertEquals(0.2, s.precision(), 1e-9);
        assertEquals(1.0, s.recall(), 1e-9);
        assertEquals(1.0 / 3.0, s.f1(), 1e-9);
        assertEquals(1.0, s.ap(), 1e-9);
        assertEquals(1.0, s.ndcg(), 1e-9);
    }

    //Test que prueba si es relevante en la última posición, se calcula correctamente precision, recall, f1, ap y ndcg
    @Test
    void relevanteEnUltimaPosicion() {
        var s = RetrievalMetrics.calculate(List.of("X1", "X2", "X3", "X4", "R1"), Set.of("R1"), 5);
        assertEquals(0.2, s.precision(), 1e-9);
        assertEquals(1.0, s.recall(), 1e-9);
        assertEquals(1.0 / 3.0, s.f1(), 1e-9);
        assertEquals(0.2, s.ap(), 1e-9);
        double idcg = 1.0;
        double dcg = 1.0 / (Math.log(6) / Math.log(2));
        assertEquals(dcg / idcg, s.ndcg(), 1e-9);
    }

    //Test que prueba si hay varios relevantes, se calcula correctamente precision, recall, f1, ap y ndcg
    @Test
    void variosRelevantesEjemploGuion() {
        var s = RetrievalMetrics.calculate(List.of("R1", "X1", "R2", "X2", "X3"), Set.of("R1", "R2", "R3"), 5);
        assertEquals(0.4, s.precision(), 1e-9);
        assertEquals(2.0 / 3.0, s.recall(), 1e-9);
        assertEquals(0.5, s.f1(), 1e-9);
        assertEquals(5.0 / 9.0, s.ap(), 1e-9);
        double dcg = 1.0 + 1.0 / (Math.log(4) / Math.log(2));
        double idcg = 1.0 + 1.0 / (Math.log(3) / Math.log(2)) + 1.0 / (Math.log(4) / Math.log(2));
        assertEquals(dcg / idcg, s.ndcg(), 1e-9);
    }

    //Test que prueba si no hay relevantes recuperados, se calcula correctamente precision, recall, f1, ap y ndcg
    @Test
    void sinRelevantesRecuperados() {
        var s = RetrievalMetrics.calculate(List.of("X1", "X2", "X3", "X4", "X5"), Set.of("R1", "R2"), 5);
        assertEquals(0.0, s.precision(), 1e-9);
        assertEquals(0.0, s.recall(), 1e-9);
        assertEquals(0.0, s.f1(), 1e-9);
        assertEquals(0.0, s.ap(), 1e-9);
        assertEquals(0.0, s.ndcg(), 1e-9);
    }

    //Test que prueba si hay más relevantes que k, se calcula correctamente precision, recall, f1, ap y ndcg
    @Test
    void masRelevantesQueK() {
        Set<String> veinticincoRelevantes = new HashSet<>();
        for (int i = 1; i <= 25; i++) veinticincoRelevantes.add("R" + i);
        var s = RetrievalMetrics.calculate(List.of("R1", "R2", "R3", "R4", "R5"), veinticincoRelevantes, 5);
        assertEquals(1.0, s.precision(), 1e-9);
        assertEquals(0.2, s.recall(), 1e-9);
        assertEquals(1.0 / 3.0, s.f1(), 1e-9);
        assertEquals(0.2, s.ap(), 1e-9);
        assertEquals(1.0, s.ndcg(), 1e-9);
    }

    //Test que prueba si no hay documentos relevantes en qrels, se calcula correctamente precision, recall, f1, ap y ndcg
    @Test
    void sinDocumentosRelevantesEnQrels() {
        var s = RetrievalMetrics.calculate(List.of("X1", "X2", "X3"), Set.of(), 5);
        assertEquals(0.0, s.precision(), 1e-9);
        assertEquals(0.0, s.recall(), 1e-9);
        assertEquals(0.0, s.f1(), 1e-9);
        assertEquals(0.0, s.ap(), 1e-9);
        assertEquals(0.0, s.ndcg(), 1e-9);
    }
}
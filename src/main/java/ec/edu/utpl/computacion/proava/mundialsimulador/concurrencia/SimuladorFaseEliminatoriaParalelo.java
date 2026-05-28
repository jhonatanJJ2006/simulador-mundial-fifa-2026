package ec.edu.utpl.computacion.proava.mundialsimulador.concurrencia;

import ec.edu.utpl.computacion.proava.mundialsimulador.modelo.ResultadoPartido;
import ec.edu.utpl.computacion.proava.mundialsimulador.simulador.GeneradorEnfrentamientos;
import ec.edu.utpl.computacion.proava.mundialsimulador.simulador.SimuladorPartido;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Simula una fase eliminatoria en paralelo.
 *
 * A diferencia de la fase de grupos, aquí los enfrentamientos vienen
 * ya armados (no se generan por round-robin). Recibe los pares y los
 * simula concurrentemente.
 *
 * En eliminatorias permiteEmpate=false: si Poisson da empate, se va a
 * penales (lógica que ya está en SimuladorPartidoFifa).
 */
public class SimuladorFaseEliminatoriaParalelo {
    private final SimuladorPartido simuladorPartido;
    private final int numeroHilos;

    public SimuladorFaseEliminatoriaParalelo(SimuladorPartido simP, int hilos) {
        this.simuladorPartido = simP;
        this.numeroHilos = hilos;
    }

    public List<ResultadoPartido> simular(List<GeneradorEnfrentamientos.Enfrentamiento> enfrentamientos) {
        try (ExecutorService pool = Executors.newFixedThreadPool(numeroHilos)) {
            List<Future<ResultadoPartido>> futures = new ArrayList<>();

            for (GeneradorEnfrentamientos.Enfrentamiento e : enfrentamientos) {
                // permiteEmpate = false en eliminatorias
                SimuladorPartidoCallable tarea = new SimuladorPartidoCallable(
                        simuladorPartido, e, false);
                futures.add(pool.submit(tarea));
            }

            List<ResultadoPartido> resultados = new ArrayList<>(futures.size());
            for (Future<ResultadoPartido> f : futures) {
                try {
                    resultados.add(f.get(60, TimeUnit.SECONDS));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrumpido", ie);
                } catch (ExecutionException ee) {
                    throw new RuntimeException("Error en partido", ee.getCause());
                } catch (TimeoutException te) {
                    throw new RuntimeException("Timeout", te);
                }
            }
            return resultados;
        }
    }
}

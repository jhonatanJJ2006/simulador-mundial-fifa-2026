package ec.edu.utpl.computacion.proava.mundialsimulador.concurrencia;

import ec.edu.utpl.computacion.proava.mundialsimulador.modelo.Grupo;
import ec.edu.utpl.computacion.proava.mundialsimulador.modelo.ResultadoPartido;
import ec.edu.utpl.computacion.proava.mundialsimulador.simulador.GeneradorEnfrentamientos;
import ec.edu.utpl.computacion.proava.mundialsimulador.simulador.SimuladorPartido;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Simulador concurrente de la fase de grupos.
 *
 * PATRÓN: fork-join por fase.
 *   1. Generar todas las tareas (los 72 enfrentamientos).
 *   2. SUBMIT: enviar todas al ExecutorService (esto es no-bloqueante).
 *   3. JOIN: esperar los resultados con future.get() (esto sí bloquea).
 *   4. Devolver los resultados ordenados.
 *
 * Punto importante: el hilo principal NO simula partidos. Su trabajo es
 * coordinar: lanza tareas, espera resultados, los recolecta. Los partidos
 * mismos los simulan los hilos del pool.
 */
public class SimuladorFaseGruposParalelo {
    private final SimuladorPartido simuladorPartido;
    private final int numeroHilos;

    /**
     * @param simuladorPartido instancia compartida entre todos los hilos.
     *                         FUNCIONA porque es thread-safe (sin estado mutable).
     * @param numeroHilos tamaño del pool. Más hilos = más paralelismo, hasta
     *                    cierto punto. 8-16 es razonable para este proyecto.
     */
    public SimuladorFaseGruposParalelo(SimuladorPartido simuladorPartido,
                                       int numeroHilos) {
        this.simuladorPartido = simuladorPartido;
        this.numeroHilos = numeroHilos;
    }

    public List<ResultadoPartido> simular(List<Grupo> grupos) {
        // Paso 1: generar tareas (idéntico al modo secuencial)
        List<GeneradorEnfrentamientos.Enfrentamiento> enfrentamientos =
                GeneradorEnfrentamientos.deTodosLosGrupos(grupos);

        // Paso 2: crear el pool de hilos
        // try-with-resources: el ExecutorService implementa AutoCloseable
        // desde Java 19. Garantiza cierre automático al final del bloque.
        try (ExecutorService pool = Executors.newFixedThreadPool(numeroHilos)) {

            // Paso 3: SUBMIT todas las tareas. Esto es RÁPIDO porque
            // no bloquea esperando: solo encola las tareas en el pool.
            List<Future<ResultadoPartido>> futures = new ArrayList<>();
            for (GeneradorEnfrentamientos.Enfrentamiento e : enfrentamientos) {
                SimuladorPartidoCallable tarea = new SimuladorPartidoCallable(
                        simuladorPartido, e, true);  // fase de grupos: permite empate
                Future<ResultadoPartido> futuro = pool.submit(tarea);
                futures.add(futuro);
            }

            // En este punto, el pool ya está trabajando: hasta N partidos
            // se están simulando EN PARALELO (donde N = numeroHilos).

            // Paso 4: JOIN. future.get() BLOQUEA al hilo principal
            // hasta que ese resultado específico esté listo. Recorremos
            // en orden, así los resultados quedan en el mismo orden
            // que los enfrentamientos originales.
            List<ResultadoPartido> resultados = new ArrayList<>(futures.size());
            for (Future<ResultadoPartido> futuro : futures) {
                try {
                    ResultadoPartido r = futuro.get(60, TimeUnit.SECONDS);
                    resultados.add(r);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Simulación interrumpida", e);
                } catch (ExecutionException e) {
                    throw new RuntimeException(
                            "Error simulando un partido", e.getCause());
                } catch (java.util.concurrent.TimeoutException e) {
                    throw new RuntimeException(
                            "Un partido tardó más de 60s, algo está mal", e);
                }
            }

            return resultados;
        }
        // Al salir del try, el pool se cierra automáticamente
    }
}

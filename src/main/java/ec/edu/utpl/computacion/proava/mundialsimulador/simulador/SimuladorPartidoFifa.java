package ec.edu.utpl.computacion.proava.mundialsimulador.simulador;

import ec.edu.utpl.computacion.proava.mundialsimulador.modelo.Equipo;
import ec.edu.utpl.computacion.proava.mundialsimulador.modelo.ResultadoPartido;
import ec.edu.utpl.computacion.proava.mundialsimulador.simulador.SimuladorPartido;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Simulador de partidos basado en puntos FIFA, con goles modelados
 * con distribución de Poisson.
 *
 * THREAD-SAFE: no tiene estado mutable. Las únicas variables son
 * constantes (final static). Cada llamada a simular() trabaja solo
 * con sus parámetros y ThreadLocalRandom (que es thread-safe sin
 * contención).
 *
 * Esto significa que UN SOLO simulador puede ser compartido por
 * múltiples hilos: una instancia, muchas simulaciones en paralelo.
 */
public class SimuladorPartidoFifa implements SimuladorPartido {

    /** Media histórica de goles por equipo por partido en mundiales. */
    private static final double MEDIA_GOLES_BASE = 1.4;

    /** Límite superior de goles para evitar resultados absurdos. */
    private static final int MAX_GOLES = 8;

    @Override
    public ResultadoPartido simular(Equipo local, Equipo visitante,
                                    boolean permiteEmpate) {
        long inicio = System.nanoTime();

        // ============================================================
        // Retardo artificial: simula que un partido "toma tiempo"
        // (en la vida real sería: queries a BD, cálculos pesados, etc.)
        // Esto NO afecta la lógica, solo hace visible el paralelismo.
        //
        // Entre 200 y 400 ms aleatorio: variabilidad realista.
        // ============================================================
        /*try {
            int retardoMs = 200 + ThreadLocalRandom.current().nextInt(200);
            Thread.sleep(retardoMs);
        } catch (InterruptedException e) {
            // Si nos interrumpen, restauramos el flag y salimos limpiamente
            Thread.currentThread().interrupt();
            throw new RuntimeException("Simulación interrumpida", e);
        }*/

        // Probabilidad de victoria del local (entre 0 y 1)
        double probLocal = calcularProbabilidad(local, visitante);

        // Lambdas (medias) de Poisson para cada equipo
        double lambdaLocal = MEDIA_GOLES_BASE * (probLocal / 0.5);
        double lambdaVisitante = MEDIA_GOLES_BASE * ((1 - probLocal) / 0.5);

        // Generar goles
        int golesLocal = poisson(lambdaLocal);
        int golesVisitante = poisson(lambdaVisitante);

        ResultadoPartido.Builder builder = ResultadoPartido.builder(local, visitante)
                .golesLocal(golesLocal)
                .golesVisitante(golesVisitante);

        // Determinar ganador
        if (golesLocal > golesVisitante) {
            builder.ganador(local);
        } else if (golesVisitante > golesLocal) {
            builder.ganador(visitante);
        } else {
            // Empate
            if (permiteEmpate) {
                // Fase de grupos: empate válido, ganador queda en null
            } else {
                // Fase eliminatoria: definir por penales
                int[] penales = simularPenales(local, visitante, probLocal);
                Equipo ganador = (penales[0] > penales[1]) ? local : visitante;
                builder.penales(penales[0], penales[1]).ganador(ganador);
            }
        }

        long duracionMs = (System.nanoTime() - inicio) / 1_000_000;
        return builder
                .duracionMs(duracionMs)
                .hiloNombre(Thread.currentThread().getName())
                .build();
    }

    // ----------------------------------------------------------------
    // Métodos auxiliares
    // ----------------------------------------------------------------

    /**
     * Calcula la probabilidad de victoria del equipo A usando escala
     * tipo ELO (exponencial), que es la estándar en sistemas de ranking
     * deportivo.
     *
     * Fórmula: P(A) = 1 / (1 + 10^((puntos_B - puntos_A) / 600))
     *
     * El divisor 600 controla cuánto importa la diferencia de puntos:
     *   - Menor (ej. 400): diferencias importan MÁS, menos sorpresas.
     *   - Mayor (ej. 800): diferencias importan MENOS, más sorpresas.
     *   - 600 es un buen equilibrio para fútbol internacional.
     *
     * Esta fórmula da resultados mucho más realistas que la razón
     * simple de puntos. Por ejemplo:
     *   - Diferencia de 100 puntos → ~64% victoria
     *   - Diferencia de 200 puntos → ~76% victoria
     *   - Diferencia de 400 puntos → ~91% victoria
     */
    private double calcularProbabilidad(Equipo a, Equipo b) {
        double diferencia = b.getPuntosFifa() - a.getPuntosFifa();
        return 1.0 / (1.0 + Math.pow(10, diferencia / 600.0));
    }

    /**
     * Genera un entero aleatorio según distribución de Poisson.
     *
     * Algoritmo de Knuth: simple y suficiente para lambdas < 30
     * (en fútbol nunca pasamos de lambda=3 más o menos).
     *
     * IMPORTANTE: usamos ThreadLocalRandom (no Math.random ni Random).
     * Cada hilo tiene su propio generador, sin contención. Es la
     * forma correcta de aleatoriedad en código concurrente.
     */
    private int poisson(double lambda) {
        double L = Math.exp(-lambda);
        int k = 0;
        double p = 1.0;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        do {
            k++;
            p *= rnd.nextDouble();
        } while (p > L && k < MAX_GOLES);

        return k - 1;
    }

    /**
     * Simula una tanda de penales como suma ponderada.
     * En vez de tirar 5+ penales uno por uno, modelo el resultado
     * agregado: cada equipo anota entre 3 y 5 penales, con probabilidad
     * proporcional a sus puntos FIFA.
     *
     * Es una simplificación. Si quisieran modelar tiro por tiro,
     * sería un Callable anidado interesante (extensión opcional).
     */
    private int[] simularPenales(Equipo a, Equipo b, double probA) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int penalesA, penalesB;

        // Sigue tirando hasta que haya un ganador (mínimo 3 cada uno)
        do {
            penalesA = 3 + (int) Math.round(rnd.nextDouble() * 2 * probA);
            penalesB = 3 + (int) Math.round(rnd.nextDouble() * 2 * (1 - probA));
        } while (penalesA == penalesB);

        return new int[] { penalesA, penalesB };
    }
}
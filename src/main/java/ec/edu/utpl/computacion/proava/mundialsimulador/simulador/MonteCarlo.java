package ec.edu.utpl.computacion.proava.mundialsimulador.simulador;

import ec.edu.utpl.computacion.proava.mundialsimulador.modelo.Equipo;
import ec.edu.utpl.computacion.proava.mundialsimulador.modelo.Grupo;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Ejecuta N simulaciones completas del Mundial y analiza la frecuencia
 * con que cada equipo gana.
 *
 * Esto es el método de MONTE CARLO: en vez de calcular analíticamente
 * la probabilidad de que cada equipo gane (matemáticamente imposible
 * dado el árbol de eliminación), simulamos muchísimas veces y miramos
 * la distribución empírica.
 *
 * Curiosidad histórica: el método se inventó en Los Alamos durante el
 * proyecto Manhattan para simular reacciones nucleares. Lo bautizaron
 * "Monte Carlo" por el casino, en referencia al rol del azar.
 */
public class MonteCarlo {
    public static class ResultadoMonteCarlo {
        public final int totalSimulaciones;
        public final Map<Equipo, Integer> campeonatosPorEquipo;
        public final Map<Equipo, Integer> finalesPorEquipo;
        public final Map<Equipo, Integer> semifinalesPorEquipo;
        public final long duracionTotalMs;

        public ResultadoMonteCarlo(int total,
                                   Map<Equipo, Integer> camp,
                                   Map<Equipo, Integer> fin,
                                   Map<Equipo, Integer> semi,
                                   long ms) {
            this.totalSimulaciones = total;
            this.campeonatosPorEquipo = camp;
            this.finalesPorEquipo = fin;
            this.semifinalesPorEquipo = semi;
            this.duracionTotalMs = ms;
        }

        public void imprimirReporte() {
            System.out.println();
            System.out.println("══════════════════════════════════════════════");
            System.out.printf("  ANÁLISIS MONTE CARLO: %d SIMULACIONES%n", totalSimulaciones);
            System.out.println("══════════════════════════════════════════════");
            System.out.printf("Tiempo total: %.1f segundos%n%n", duracionTotalMs / 1000.0);

            System.out.println("Top 10 - Frecuencia de campeonato:");
            System.out.println("-----------------------------------");
            campeonatosPorEquipo.entrySet().stream()
                    .sorted(Map.Entry.<Equipo, Integer>comparingByValue().reversed())
                    .limit(10)
                    .forEach(e -> {
                        double pct = 100.0 * e.getValue() / totalSimulaciones;
                        System.out.printf("  %-22s %4d veces  (%.1f%%)%n",
                                e.getKey().getNombre(), e.getValue(), pct);
                    });

            System.out.println("\nTop 10 - Llegadas a la final:");
            System.out.println("-----------------------------------");
            finalesPorEquipo.entrySet().stream()
                    .sorted(Map.Entry.<Equipo, Integer>comparingByValue().reversed())
                    .limit(10)
                    .forEach(e -> {
                        double pct = 100.0 * e.getValue() / totalSimulaciones;
                        System.out.printf("  %-22s %4d veces  (%.1f%%)%n",
                                e.getKey().getNombre(), e.getValue(), pct);
                    });
        }
    }

    private final SimuladorTorneo simuladorTorneo;

    public MonteCarlo(SimuladorTorneo simuladorTorneo) {
        this.simuladorTorneo = simuladorTorneo;
    }

    public ResultadoMonteCarlo ejecutar(List<Grupo> grupos, int n) {
        Map<Equipo, Integer> campeonatos = new HashMap<>();
        Map<Equipo, Integer> finales = new HashMap<>();
        Map<Equipo, Integer> semifinales = new HashMap<>();

        long inicio = System.nanoTime();

        for (int i = 1; i <= n; i++) {
            SimuladorTorneo.ResultadoTorneo r = simuladorTorneo.ejecutar(grupos,
                    String.format("Montecarlo %d/%d", i, n));

            // Contar campeonato
            campeonatos.merge(r.campeon, 1, Integer::sum);

            // Contar finales (campeón y subcampeón llegaron a la final)
            finales.merge(r.campeon, 1, Integer::sum);
            finales.merge(r.subcampeon, 1, Integer::sum);

            // Contar semifinales (tercer lugar + finalistas llegaron a semis;
            // el cuarto lugar también, pero no lo registramos por simplicidad)
            semifinales.merge(r.campeon, 1, Integer::sum);
            semifinales.merge(r.subcampeon, 1, Integer::sum);
            semifinales.merge(r.tercerLugar, 1, Integer::sum);

            // Progreso visible: cada 10 simulaciones
            if (i % 10 == 0 || i == n) {
                System.out.printf("  Progreso: %d/%d simulaciones%n", i, n);
            }
        }

        long duracion = (System.nanoTime() - inicio) / 1_000_000;
        return new ResultadoMonteCarlo(n, campeonatos, finales, semifinales, duracion);
    }
}

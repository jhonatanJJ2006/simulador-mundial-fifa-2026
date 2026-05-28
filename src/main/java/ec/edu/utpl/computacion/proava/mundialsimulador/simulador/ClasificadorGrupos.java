package ec.edu.utpl.computacion.proava.mundialsimulador.simulador;

import ec.edu.utpl.computacion.proava.mundialsimulador.modelo.Equipo;
import ec.edu.utpl.computacion.proava.mundialsimulador.modelo.Grupo;
import ec.edu.utpl.computacion.proava.mundialsimulador.modelo.ResultadoPartido;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Clasifica los equipos dentro de un grupo según las reglas FIFA.
 *
 * Criterios de desempate (en este orden):
 *   1. Puntos (3 por victoria, 1 por empate, 0 por derrota)
 *   2. Diferencia de goles (a favor - en contra)
 *   3. Goles a favor
 *   4. Puntos FIFA (criterio adicional que agregamos para evitar
 *      empates totales sin recurrir a sorteo)
 */
public class ClasificadorGrupos {
    /**
     * Tabla de un equipo dentro de su grupo: estadísticas acumuladas.
     */
    public static class EstadisticaEquipo {
        public final Equipo equipo;
        public int partidosJugados = 0;
        public int victorias = 0;
        public int empates = 0;
        public int derrotas = 0;
        public int golesFavor = 0;
        public int golesContra = 0;

        public EstadisticaEquipo(Equipo equipo) {
            this.equipo = equipo;
        }

        public int puntos() { return victorias * 3 + empates; }
        public int diferenciaGoles() { return golesFavor - golesContra; }

        @Override
        public String toString() {
            return String.format("%-20s PJ:%d V:%d E:%d D:%d GF:%d GC:%d DG:%+d Pts:%d",
                    equipo.getNombre(), partidosJugados, victorias, empates,
                    derrotas, golesFavor, golesContra, diferenciaGoles(), puntos());
        }
    }

    /**
     * Calcula la tabla de posiciones de un grupo y devuelve los equipos
     * ordenados del primero al cuarto.
     */
    public List<EstadisticaEquipo> clasificar(Grupo grupo,
                                              List<ResultadoPartido> resultadosGrupo) {
        // Inicializar estadísticas en cero para los 4 equipos
        Map<Equipo, EstadisticaEquipo> tabla = new HashMap<>();
        for (Equipo e : grupo.getEquipos()) {
            tabla.put(e, new EstadisticaEquipo(e));
        }

        // Acumular resultados
        for (ResultadoPartido r : resultadosGrupo) {
            EstadisticaEquipo statLocal = tabla.get(r.getEquipoLocal());
            EstadisticaEquipo statVis = tabla.get(r.getEquipoVisitante());

            // Verificar que el partido pertenece a este grupo
            if (statLocal == null || statVis == null) continue;

            statLocal.partidosJugados++;
            statVis.partidosJugados++;
            statLocal.golesFavor += r.getGolesLocal();
            statLocal.golesContra += r.getGolesVisitante();
            statVis.golesFavor += r.getGolesVisitante();
            statVis.golesContra += r.getGolesLocal();

            if (r.esEmpate()) {
                statLocal.empates++;
                statVis.empates++;
            } else if (r.getGanador().equals(r.getEquipoLocal())) {
                statLocal.victorias++;
                statVis.derrotas++;
            } else {
                statVis.victorias++;
                statLocal.derrotas++;
            }
        }

        // Ordenar por los criterios FIFA
        return tabla.values().stream()
                .sorted(comparadorFifa())
                .collect(Collectors.toList());
    }

    /**
     * Comparador que implementa los criterios de desempate FIFA.
     * Encadenamos comparators con thenComparing() para legibilidad.
     */
    private Comparator<EstadisticaEquipo> comparadorFifa() {
        return Comparator
                .comparingInt(EstadisticaEquipo::puntos).reversed()
                .thenComparing(Comparator.comparingInt(
                        EstadisticaEquipo::diferenciaGoles).reversed())
                .thenComparing(Comparator.comparingInt(
                        (EstadisticaEquipo e) -> e.golesFavor).reversed())
                .thenComparing(Comparator.comparingDouble(
                        (EstadisticaEquipo e) -> e.equipo.getPuntosFifa()).reversed());
    }
}

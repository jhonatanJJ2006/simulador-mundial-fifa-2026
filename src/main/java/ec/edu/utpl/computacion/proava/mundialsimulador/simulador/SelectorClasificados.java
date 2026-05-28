package ec.edu.utpl.computacion.proava.mundialsimulador.simulador;

import ec.edu.utpl.computacion.proava.mundialsimulador.modelo.Equipo;
import ec.edu.utpl.computacion.proava.mundialsimulador.modelo.Grupo;
import ec.edu.utpl.computacion.proava.mundialsimulador.modelo.ResultadoPartido;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Determina los 32 clasificados a 16avos:
 *   - 24 primeros y segundos de los 12 grupos
 *   - 8 mejores terceros lugares
 */
public class SelectorClasificados {
    private final ClasificadorGrupos clasificador = new ClasificadorGrupos();


    public static class ResultadoSeleccion {
        public final List<Equipo> clasificados;
        public final Map<Grupo, List<ClasificadorGrupos.EstadisticaEquipo>> tablasPorGrupo;

        public ResultadoSeleccion(List<Equipo> clasificados,
                                  Map<Grupo, List<ClasificadorGrupos.EstadisticaEquipo>> tablas) {
            this.clasificados = clasificados;
            this.tablasPorGrupo = tablas;
        }
    }

    public ResultadoSeleccion seleccionar(List<Grupo> grupos,
                                          List<ResultadoPartido> todosLosResultados) {
        List<Equipo> clasificados = new ArrayList<>(32);
        List<ClasificadorGrupos.EstadisticaEquipo> terceros = new ArrayList<>();
        Map<Grupo, List<ClasificadorGrupos.EstadisticaEquipo>> tablasPorGrupo = new LinkedHashMap<>();

        for (Grupo g : grupos) {
            List<ResultadoPartido> resGrupo = todosLosResultados.stream()
                    .filter(r -> g.getEquipos().contains(r.getEquipoLocal())
                            && g.getEquipos().contains(r.getEquipoVisitante()))
                    .collect(Collectors.toList());

            List<ClasificadorGrupos.EstadisticaEquipo> tabla = clasificador.clasificar(g, resGrupo);
            tablasPorGrupo.put(g, tabla);  // <-- guardamos la tabla

            clasificados.add(tabla.get(0).equipo);
            clasificados.add(tabla.get(1).equipo);
            terceros.add(tabla.get(2));
        }

        // Ordenar los 12 terceros por criterios FIFA y tomar los 8 mejores
        terceros.sort(Comparator
                .comparingInt(ClasificadorGrupos.EstadisticaEquipo::puntos).reversed()
                .thenComparing(Comparator.comparingInt(
                        ClasificadorGrupos.EstadisticaEquipo::diferenciaGoles).reversed())
                .thenComparing(Comparator.comparingInt(
                        (ClasificadorGrupos.EstadisticaEquipo e) -> e.golesFavor).reversed())
                .thenComparing(Comparator.comparingDouble(
                        (ClasificadorGrupos.EstadisticaEquipo e) -> e.equipo.getPuntosFifa()).reversed()));

        for (int i = 0; i < 8; i++) {
            clasificados.add(terceros.get(i).equipo);
        }

        return new ResultadoSeleccion(clasificados, tablasPorGrupo);
    }
}

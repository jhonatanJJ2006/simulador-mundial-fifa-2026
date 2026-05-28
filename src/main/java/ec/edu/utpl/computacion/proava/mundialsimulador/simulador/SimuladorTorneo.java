package ec.edu.utpl.computacion.proava.mundialsimulador.simulador;

import ec.edu.utpl.computacion.proava.mundialsimulador.concurrencia.SimuladorFaseEliminatoriaParalelo;
import ec.edu.utpl.computacion.proava.mundialsimulador.concurrencia.SimuladorFaseGruposParalelo;
import ec.edu.utpl.computacion.proava.mundialsimulador.dao.SimulacionDAO;
import ec.edu.utpl.computacion.proava.mundialsimulador.modelo.Equipo;
import ec.edu.utpl.computacion.proava.mundialsimulador.modelo.Grupo;
import ec.edu.utpl.computacion.proava.mundialsimulador.modelo.ResultadoPartido;

import java.util.ArrayList;
import java.util.List;

/**
 * Orquesta una simulación completa del Mundial 2026.
 *
 * Estructura: fork-join por FASES. Cada fase se ejecuta en paralelo
 * internamente, pero las fases entre sí son secuenciales (la fase N
 * espera a que termine N-1 porque necesita sus ganadores).
 *
 *   Fase de grupos       → 72 partidos en paralelo
 *      ↓ esperar a todos
 *   Determinar 32 clasificados
 *      ↓
 *   16avos               → 16 partidos en paralelo
 *      ↓
 *   Octavos              → 8 en paralelo
 *      ↓
 *   Cuartos              → 4 en paralelo
 *      ↓
 *   Semifinales          → 2 en paralelo
 *      ↓
 *   Tercer lugar + Final → 2 en paralelo
 *      ↓
 *   ¡Campeón!
 */
public class SimuladorTorneo {
    private final SimuladorFaseGruposParalelo simGrupos;
    private final SimuladorFaseEliminatoriaParalelo simElim;
    private final SelectorClasificados selector;
    private final SimulacionDAO simulacionDAO;
    private final boolean persistir;

    public static class ResultadoTorneo {
        public final int simulacionId;
        public final Equipo campeon;
        public final Equipo subcampeon;
        public final Equipo tercerLugar;
        public final long duracionTotalMs;

        public ResultadoTorneo(int id, Equipo camp, Equipo sub, Equipo tercer, long ms) {
            this.simulacionId = id;
            this.campeon = camp;
            this.subcampeon = sub;
            this.tercerLugar = tercer;
            this.duracionTotalMs = ms;
        }
    }

    public SimuladorTorneo(SimuladorPartido simP, int hilos,
                           SimulacionDAO simulacionDAO, boolean persistir) {
        this.simGrupos = new SimuladorFaseGruposParalelo(simP, hilos);
        this.simElim = new SimuladorFaseEliminatoriaParalelo(simP, hilos);
        this.selector = new SelectorClasificados();
        this.simulacionDAO = simulacionDAO;
        this.persistir = persistir;
    }

    public ResultadoTorneo ejecutar(List<Grupo> grupos, String observaciones) {
        long inicio = System.nanoTime();

        // Crear registro de simulación (si persistimos)
        int simId = persistir
                ? simulacionDAO.crearSimulacion(null, observaciones)
                : -1;

        // ====== FASE DE GRUPOS ======
        List<ResultadoPartido> resGrupos = simGrupos.simular(grupos);
        if (persistir) {
            simulacionDAO.guardarPartidos(simId, "GRUPOS", resGrupos, grupos);
        }

        // ====== CLASIFICADOS (32) ======
        SelectorClasificados.ResultadoSeleccion seleccion = selector.seleccionar(grupos, resGrupos);
        List<Equipo> clasificados = seleccion.clasificados;

        if (persistir) {
            simulacionDAO.guardarEstadisticasGrupos(simId, seleccion.tablasPorGrupo);
        }

        // ====== 16AVOS ======
        List<GeneradorEnfrentamientos.Enfrentamiento> e16 = emparejar16avos(clasificados);
        List<ResultadoPartido> r16 = simElim.simular(e16);
        if (persistir) simulacionDAO.guardarPartidos(simId, "16AVOS", r16, null);
        List<Equipo> g16 = extraerGanadores(r16);

        // ====== OCTAVOS ======
        List<GeneradorEnfrentamientos.Enfrentamiento> eOct = emparejar(g16);
        List<ResultadoPartido> rOct = simElim.simular(eOct);
        if (persistir) simulacionDAO.guardarPartidos(simId, "OCTAVOS", rOct, null);
        List<Equipo> gOct = extraerGanadores(rOct);

        // ====== CUARTOS ======
        List<GeneradorEnfrentamientos.Enfrentamiento> eCua = emparejar(gOct);
        List<ResultadoPartido> rCua = simElim.simular(eCua);
        if (persistir) simulacionDAO.guardarPartidos(simId, "CUARTOS", rCua, null);
        List<Equipo> gCua = extraerGanadores(rCua);

        // ====== SEMIFINALES ======
        List<GeneradorEnfrentamientos.Enfrentamiento> eSemi = emparejar(gCua);
        List<ResultadoPartido> rSemi = simElim.simular(eSemi);
        if (persistir) simulacionDAO.guardarPartidos(simId, "SEMIFINAL", rSemi, null);
        List<Equipo> finalistas = extraerGanadores(rSemi);
        List<Equipo> perdedoresSemi = extraerPerdedores(rSemi);

        // ====== TERCER LUGAR y FINAL (en paralelo, son independientes) ======
        List<GeneradorEnfrentamientos.Enfrentamiento> eCierre = List.of(
                new GeneradorEnfrentamientos.Enfrentamiento(perdedoresSemi.get(0), perdedoresSemi.get(1), null),
                new GeneradorEnfrentamientos.Enfrentamiento(finalistas.get(0), finalistas.get(1), null)
        );
        List<ResultadoPartido> rCierre = simElim.simular(eCierre);

        // El primero de la lista es el tercer lugar, el segundo es la final
        ResultadoPartido partidoTercero = rCierre.get(0);
        ResultadoPartido partidoFinal = rCierre.get(1);

        if (persistir) {
            simulacionDAO.guardarPartidos(simId, "TERCER_LUGAR",
                    List.of(partidoTercero), null);
            simulacionDAO.guardarPartidos(simId, "FINAL",
                    List.of(partidoFinal), null);
        }

        Equipo campeon = partidoFinal.getGanador();
        Equipo subcampeon = campeon.equals(partidoFinal.getEquipoLocal())
                ? partidoFinal.getEquipoVisitante()
                : partidoFinal.getEquipoLocal();
        Equipo tercerLugar = partidoTercero.getGanador();

        if (persistir) {
            simulacionDAO.actualizarPodio(simId, campeon, subcampeon, tercerLugar);
        }

        long duracionMs = (System.nanoTime() - inicio) / 1_000_000;
        return new ResultadoTorneo(simId, campeon, subcampeon, tercerLugar, duracionMs);
    }

    // ----------------------------------------------------------------
    // Auxiliares
    // ----------------------------------------------------------------

    /**
     * Empareja los 32 clasificados para 16avos.
     * NOTA: este es un emparejamiento SIMPLIFICADO. FIFA tiene un
     * "bracket" oficial complejo basado en los grupos de origen.
     * Para fines del simulador usamos un emparejamiento directo.
     */
    private List<GeneradorEnfrentamientos.Enfrentamiento> emparejar16avos(List<Equipo> clasificados) {
        List<GeneradorEnfrentamientos.Enfrentamiento> pares = new ArrayList<>();
        for (int i = 0; i < clasificados.size(); i += 2) {
            pares.add(new GeneradorEnfrentamientos.Enfrentamiento(
                    clasificados.get(i),
                    clasificados.get(i + 1),
                    null));
        }
        return pares;
    }

    /**
     * Empareja una lista de ganadores en orden secuencial.
     */
    private List<GeneradorEnfrentamientos.Enfrentamiento> emparejar(List<Equipo> equipos) {
        List<GeneradorEnfrentamientos.Enfrentamiento> pares = new ArrayList<>();
        for (int i = 0; i < equipos.size(); i += 2) {
            pares.add(new GeneradorEnfrentamientos.Enfrentamiento(equipos.get(i), equipos.get(i + 1), null));
        }
        return pares;
    }

    private List<Equipo> extraerGanadores(List<ResultadoPartido> resultados) {
        List<Equipo> ganadores = new ArrayList<>();
        for (ResultadoPartido r : resultados) {
            ganadores.add(r.getGanador());
        }
        return ganadores;
    }

    private List<Equipo> extraerPerdedores(List<ResultadoPartido> resultados) {
        List<Equipo> perdedores = new ArrayList<>();
        for (ResultadoPartido r : resultados) {
            Equipo perdedor = r.getGanador().equals(r.getEquipoLocal())
                    ? r.getEquipoVisitante()
                    : r.getEquipoLocal();
            perdedores.add(perdedor);
        }
        return perdedores;
    }
}

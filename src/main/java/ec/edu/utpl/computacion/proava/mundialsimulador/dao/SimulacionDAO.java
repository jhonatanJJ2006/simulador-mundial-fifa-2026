package ec.edu.utpl.computacion.proava.mundialsimulador.dao;

import ec.edu.utpl.computacion.proava.mundialsimulador.db.ConnectionManager;
import ec.edu.utpl.computacion.proava.mundialsimulador.modelo.Equipo;
import ec.edu.utpl.computacion.proava.mundialsimulador.modelo.Grupo;
import ec.edu.utpl.computacion.proava.mundialsimulador.modelo.ResultadoPartido;
import ec.edu.utpl.computacion.proava.mundialsimulador.simulador.ClasificadorGrupos;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Map;

/**
 * DAO para escribir resultados de simulación en la BD.
 *
 * Pensado para uso DESPUÉS del paralelismo: el hilo coordinador llama
 * a este DAO cuando ya tiene todos los resultados recolectados. Por
 * eso no necesita ser thread-safe.
 *
 * Usa batch updates para eficiencia: insertar 72 partidos uno por uno
 * sería lento por el round-trip a la BD. PreparedStatement.addBatch()
 * agrupa los inserts y los envía en bloque.
 */
public class SimulacionDAO {
    /**
     * Crea un registro de simulación nueva y retorna su id.
     * Los campos campeon/subcampeon/tercer_lugar quedan en NULL,
     * se actualizan al final cuando se conozcan.
     */
    public int crearSimulacion(Long semilla, String observaciones) {
        String sql = """
            INSERT INTO simulaciones (semilla, observaciones)
            VALUES (?, ?)
            """;

        try (Connection con = ConnectionManager.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {

            if (semilla != null) {
                ps.setLong(1, semilla);
            } else {
                ps.setNull(1, Types.BIGINT);
            }
            ps.setString(2, observaciones);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
                throw new DAOException("No se generó id para la simulación");
            }
        } catch (SQLException e) {
            throw new DAOException("Error creando simulación", e);
        }
    }

    /**
     * Persiste TODOS los partidos de una fase en una sola operación batch.
     * Mucho más rápido que llamar al insert 72 veces.
     */
    public void guardarPartidos(int simulacionId, String fase,
                                List<ResultadoPartido> resultados,
                                List<Grupo> gruposOpcional) {
        String sql = """
            INSERT INTO partidos (
                simulacion_id, fase, grupo_id,
                equipo_local_id, equipo_visitante_id,
                goles_local, goles_visitante,
                goles_local_penales, goles_visitante_penales,
                ganador_id, duracion_ms, hilo_nombre
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection con = ConnectionManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            // Importante: deshabilitar autocommit para que el batch
            // sea una sola transacción. Si algo falla, nada se persiste.
            con.setAutoCommit(false);

            for (ResultadoPartido r : resultados) {
                ps.setInt(1, simulacionId);
                ps.setString(2, fase);

                // grupo_id: solo aplica en fase de grupos
                Integer grupoId = encontrarGrupoId(r, gruposOpcional);
                if (grupoId != null) {
                    ps.setInt(3, grupoId);
                } else {
                    ps.setNull(3, Types.INTEGER);
                }

                ps.setInt(4, r.getEquipoLocal().getId());
                ps.setInt(5, r.getEquipoVisitante().getId());
                ps.setInt(6, r.getGolesLocal());
                ps.setInt(7, r.getGolesVisitante());

                // Penales: solo si los hubo
                if (r.definidoPorPenales()) {
                    ps.setInt(8, r.getGolesLocalPenales());
                    ps.setInt(9, r.getGolesVisitantePenales());
                } else {
                    ps.setNull(8, Types.INTEGER);
                    ps.setNull(9, Types.INTEGER);
                }

                // Ganador: null si empate en grupos
                if (r.getGanador() != null) {
                    ps.setInt(10, r.getGanador().getId());
                } else {
                    ps.setNull(10, Types.INTEGER);
                }

                ps.setLong(11, r.getDuracionMs());
                ps.setString(12, r.getHiloNombre());

                ps.addBatch();
            }

            ps.executeBatch();
            con.commit();

        } catch (SQLException e) {
            throw new DAOException(
                    "Error guardando partidos de fase " + fase, e);
        }
    }

    /**
     * Guarda las estadísticas finales de los equipos en sus grupos
     * para una simulación. Una fila por equipo por grupo (48 filas total).
     */
    public void guardarEstadisticasGrupos(int simulacionId,
                                          Map<Grupo, List<ClasificadorGrupos.EstadisticaEquipo>> tablasPorGrupo) {
        String sql = """
        INSERT INTO estadisticas_grupo (
            simulacion_id, grupo_id, equipo_id,
            partidos_jugados, victorias, empates, derrotas,
            goles_favor, goles_contra, puntos, posicion_final
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection con = ConnectionManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            con.setAutoCommit(false);

            for (Map.Entry<Grupo, List<ClasificadorGrupos.EstadisticaEquipo>> entry : tablasPorGrupo.entrySet()) {
                Grupo g = entry.getKey();
                List<ClasificadorGrupos.EstadisticaEquipo> tabla = entry.getValue();

                // tabla viene ordenada del 1° al 4° por el ClasificadorGrupos
                for (int posicion = 0; posicion < tabla.size(); posicion++) {
                    ClasificadorGrupos.EstadisticaEquipo est = tabla.get(posicion);
                    ps.setInt(1, simulacionId);
                    ps.setInt(2, g.getId());
                    ps.setInt(3, est.equipo.getId());
                    ps.setInt(4, est.partidosJugados);
                    ps.setInt(5, est.victorias);
                    ps.setInt(6, est.empates);
                    ps.setInt(7, est.derrotas);
                    ps.setInt(8, est.golesFavor);
                    ps.setInt(9, est.golesContra);
                    ps.setInt(10, est.puntos());
                    ps.setInt(11, posicion + 1);  // 1 a 4
                    ps.addBatch();
                }
            }
            ps.executeBatch();
            con.commit();
        } catch (SQLException e) {
            throw new DAOException("Error guardando estadísticas de grupos", e);
        }
    }

    /**
     * Actualiza el campeón, subcampeón y tercer lugar al cerrar el torneo.
     */
    public void actualizarPodio(int simulacionId, Equipo campeon,
                                Equipo subcampeon, Equipo tercerLugar) {
        String sql = """
            UPDATE simulaciones
            SET campeon_id = ?, subcampeon_id = ?, tercer_lugar_id = ?
            WHERE id = ?
            """;

        try (Connection con = ConnectionManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, campeon.getId());
            ps.setInt(2, subcampeon.getId());
            ps.setInt(3, tercerLugar.getId());
            ps.setInt(4, simulacionId);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new DAOException("Error actualizando podio", e);
        }
    }

    /**
     * Busca el id del grupo al que pertenecen los equipos del partido.
     * Solo aplica en fase de grupos; en eliminatorias retorna null.
     */
    private Integer encontrarGrupoId(ResultadoPartido r, List<Grupo> grupos) {
        if (grupos == null) return null;

        for (Grupo g : grupos) {
            if (g.getEquipos().contains(r.getEquipoLocal())
                    && g.getEquipos().contains(r.getEquipoVisitante())) {
                return g.getId();
            }
        }
        return null;
    }
}

package ec.edu.utpl.computacion.proava.mundialsimulador.app;

import ec.edu.utpl.computacion.proava.mundialsimulador.dao.ConfederacionDAO;
import ec.edu.utpl.computacion.proava.mundialsimulador.dao.EquipoDAO;
import ec.edu.utpl.computacion.proava.mundialsimulador.dao.GrupoDAO;
import ec.edu.utpl.computacion.proava.mundialsimulador.modelo.*;
import ec.edu.utpl.computacion.proava.mundialsimulador.simulador.SimuladorPartido;
import ec.edu.utpl.computacion.proava.mundialsimulador.simulador.SimuladorPartidoFifa;
import java.util.List;

/**
 * Hello world!
 */
public class App {

    static void main(String[] args) {
        System.out.println("=== Simulador Mundial 2026 - UTPL");
        System.out.println("Programación Avanzada - Abril-Agosto 2026");
        System.out.println();

        ConfederacionDAO confDAO = new ConfederacionDAO();
        EquipoDAO equipoDAO = new EquipoDAO();
        GrupoDAO grupoDAO = new GrupoDAO();

        mostrarResumen(equipoDAO, confDAO);
        mostrarTop10(equipoDAO);
        mostrarGrupos(grupoDAO);
        mostrarAnfitriones(equipoDAO);
        probarBusquedaPorCodigo(equipoDAO);

        probarSimuladorConGrupoE(grupoDAO);
    }

    private static void mostrarResumen(
        EquipoDAO equipoDAO,
        ConfederacionDAO confDAO
    ) {
        System.out.println("Equipos clasificados por confederación:");
        System.out.println("---------------------------------------");

        List<Confederacion> confederaciones = confDAO.listarTodos();
        long total = 0;
        for (Confederacion c : confederaciones) {
            List<Equipo> equipos = equipoDAO.listarPorConfederacion(c.getId());
            System.out.printf(
                "  %-10s %2d equipos%n",
                c.getCodigo(),
                equipos.size()
            );
            total += equipos.size();
        }

        System.out.println("---------------------------------------");
        System.out.printf("  TOTAL      %2d equipos%n%n", total);
    }

    private static void mostrarTop10(EquipoDAO equipoDAO) {
        System.out.println("Top 10 equipos por ranking FIFA:");
        System.out.println("--------------------------------");
        List<Equipo> equipos = equipoDAO.listarTodos();
        equipos
            .stream()
            .limit(10)
            .forEach(e -> System.out.println("  " + e));
        System.out.println();
    }

    private static void mostrarAnfitriones(EquipoDAO equipoDAO) {
        System.out.println("Países anfitriones:");
        System.out.println("-------------------");
        equipoDAO
            .listarTodos()
            .stream()
            .filter(Equipo::isAnfitrion)
            .forEach(e -> System.out.println("  " + e));
        System.out.println();
    }

    private static void probarBusquedaPorCodigo(EquipoDAO equipoDAO) {
        System.out.println("Búsqueda por código ISO:");
        System.out.println("------------------------");
        equipoDAO
            .buscarPorCodigoIso("ECU")
            .ifPresentOrElse(
                e -> System.out.println("  Encontrado: " + e),
                () -> System.out.println("  ECU no encontrado")
            );
        equipoDAO
            .buscarPorCodigoIso("XXX")
            .ifPresentOrElse(
                e -> System.out.println("  Encontrado: " + e),
                () -> System.out.println("  XXX no existe (correcto)")
            );
    }

    private static void mostrarGrupos(GrupoDAO grupoDAO) {
        System.out.println("Grupos del Mundial 2026 (sorteo oficial):");
        System.out.println("=========================================");

        List<Grupo> grupos = grupoDAO.listarTodos();

        for (Grupo g : grupos) {
            // Promedio de puntos FIFA del grupo: indicador de "dificultad"
            double promedioPuntos = g
                .getEquipos()
                .stream()
                .mapToDouble(Equipo::getPuntosFifa)
                .average()
                .orElse(0.0);

            System.out.printf(
                "%nGrupo %s  (promedio: %.1f pts FIFA)%n",
                g.getNombre(),
                promedioPuntos
            );
            System.out.println("---------");

            for (Equipo e : g.getEquipos()) {
                System.out.printf("  %s%n", e);
            }
        }
        System.out.println();
    }

    private static void probarSimuladorConGrupoE(GrupoDAO grupoDAO) {
        System.out.println();
        System.out.println("Prueba del simulador: Grupo E del Mundial");
        System.out.println("==========================================");

        Grupo grupoE = grupoDAO
            .buscarPorNombre("E")
            .orElseThrow(() -> new RuntimeException("Grupo E no encontrado"));

        SimuladorPartido simulador = new SimuladorPartidoFifa();

        List<Equipo> equipos = grupoE.getEquipos();
        Equipo alemania = equipos.get(0);
        Equipo curazao = equipos.get(1);
        Equipo costaMarfil = equipos.get(2);
        Equipo ecuador = equipos.get(3);

        System.out.println("\nUna simulación de cada partido de Ecuador:");
        for (Equipo rival : List.of(alemania, curazao, costaMarfil)) {
            ResultadoPartido r = simulador.simular(ecuador, rival, true);
            System.out.println("  " + r);
        }

        System.out.println("\n1000 simulaciones de Ecuador vs Alemania:");
        int ganaEcu = 0, empatesGer = 0, ganaGer = 0;
        int totalGolesEcuGer = 0, totalGolesGer = 0;
        for (int n = 0; n < 1000; n++) {
            ResultadoPartido r = simulador.simular(ecuador, alemania, true);
            totalGolesEcuGer += r.getGolesLocal();
            totalGolesGer += r.getGolesVisitante();
            if (r.esEmpate()) empatesGer++;
            else if (r.getGanador().equals(ecuador)) ganaEcu++;
            else ganaGer++;
        }
        System.out.printf("  Ecuador gana: %3d%% (%d veces)%n", ganaEcu / 10, ganaEcu);
        System.out.printf("  Empate:       %3d%% (%d veces)%n", empatesGer / 10, empatesGer);
        System.out.printf("  Alemania gana:%3d%% (%d veces)%n", ganaGer / 10, ganaGer);
        System.out.printf(
            "  Goles promedio: Ecuador %.2f - Alemania %.2f%n",
            totalGolesEcuGer / 1000.0,
            totalGolesGer / 1000.0
        );

        System.out.println("\n1000 simulaciones de Ecuador vs Curazao:");
        int ganaEcuCuw = 0, empatesCuw = 0, ganaCuw = 0;
        int totalGolesEcuCuw = 0, totalGolesCuw = 0;
        for (int n = 0; n < 1000; n++) {
            ResultadoPartido r = simulador.simular(ecuador, curazao, true);
            totalGolesEcuCuw += r.getGolesLocal();
            totalGolesCuw += r.getGolesVisitante();
            if (r.esEmpate()) empatesCuw++;
            else if (r.getGanador().equals(ecuador)) ganaEcuCuw++;
            else ganaCuw++;
        }
        System.out.printf("  Ecuador gana: %3d%% (%d veces)%n", ganaEcuCuw / 10, ganaEcuCuw);
        System.out.printf("  Empate:       %3d%% (%d veces)%n", empatesCuw / 10, empatesCuw);
        System.out.printf("  Curazao gana: %3d%% (%d veces)%n", ganaCuw / 10, ganaCuw);
        System.out.printf(
            "  Goles promedio: Ecuador %.2f - Curazao %.2f%n",
            totalGolesEcuCuw / 1000.0,
            totalGolesCuw / 1000.0
        );

        System.out.println("\n1000 simulaciones de Ecuador vs Costa de Marfil:");
        int ganaEcuCiv = 0, empatesCiv = 0, ganaCiv = 0;
        int totalGolesEcuCiv = 0, totalGolesCiv = 0;
        for (int n = 0; n < 1000; n++) {
            ResultadoPartido r = simulador.simular(ecuador, costaMarfil, true);
            totalGolesEcuCiv += r.getGolesLocal();
            totalGolesCiv += r.getGolesVisitante();
            if (r.esEmpate()) empatesCiv++;
            else if (r.getGanador().equals(ecuador)) ganaEcuCiv++;
            else ganaCiv++;
        }
        System.out.printf("  Ecuador gana: %3d%% (%d veces)%n", ganaEcuCiv / 10, ganaEcuCiv);
        System.out.printf("  Empate:       %3d%% (%d veces)%n", empatesCiv / 10, empatesCiv);
        System.out.printf("  Costa de Marfil gana: %3d%% (%d veces)%n", ganaCiv / 10, ganaCiv);
        System.out.printf(
            "  Goles promedio: Ecuador %.2f - Costa de Marfil %.2f%n",
            totalGolesEcuCiv / 1000.0,
            totalGolesCiv / 1000.0
        );
    }
}

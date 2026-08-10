package engine.simulation;

import engine.simulation.modules.CollisionModule;
import engine.simulation.modules.FreeFallModule;
import engine.simulation.modules.InclinedPlaneModule;
import engine.simulation.modules.PendulumModule;
import engine.simulation.modules.ProjectileModule;
import engine.simulation.modules.SolarSystemModule;
import engine.simulation.modules.SpringModule;
import engine.simulation.modules.TugOfWarModule;

/**
 * Registro central de todos os módulos do SimulaFísica 3D.
 *
 * Responsabilidade única: instanciar e registrar os módulos
 * no ModuleManager, configurando desafios de cada um.
 *
 * Para adicionar um novo módulo ao sistema:
 *   1. Criar a classe em engine.simulation.modules
 *   2. Adicionar uma linha em registerAll()
 *   3. Pronto — a UI e o Engine detectam automaticamente
 */
public final class ModuleRegistry {

    private ModuleRegistry() {} // utilitária estática

    /**
     * Registra todos os módulos no gerenciador e retorna o primeiro ID.
     * @return ID do módulo a ser ativado por padrão.
     */
    public static String registerAll(ModuleManager manager) {

        // ── Módulo 1: Queda Livre ──────────────────
        FreeFallModule freeFall = new FreeFallModule();
        freeFall.setChallenges(FreeFallModule.buildChallenges());
        manager.register(freeFall);

        // ── Módulo 2: Projéteis ────────────────────
        ProjectileModule projectile = new ProjectileModule();
        projectile.setChallenges(ProjectileModule.buildChallenges());
        manager.register(projectile);

        // ── Módulo 3: Colisões ─────────────────────
        CollisionModule collision = new CollisionModule();
        collision.setChallenges(CollisionModule.buildChallenges());
        manager.register(collision);

        manager.register(new InclinedPlaneModule());
        manager.register(new PendulumModule());
        manager.register(new SpringModule());

        // O Sistema Solar tambem hospeda a missao de foguete interplanetaria.
        SolarSystemModule solarSystem = new SolarSystemModule();
        solarSystem.setChallenges(SolarSystemModule.buildChallenges());
        manager.register(solarSystem);

        TugOfWarModule tugOfWar = new TugOfWarModule();
        manager.register(tugOfWar);

        // Retorna o ID padrão de ativação
        return "free_fall";
    }
}

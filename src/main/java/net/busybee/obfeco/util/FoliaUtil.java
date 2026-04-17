package net.busybee.obfeco.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.TimeUnit;

public class FoliaUtil {
    private static final boolean FOLIA = classExists("io.papermc.paper.threadedregions.RegionizedServer");

    private FoliaUtil() {
    }

    public static boolean isFolia() {
        return FOLIA;
    }

    public static void run(Plugin plugin, Runnable task) {
        if (!FOLIA) {
            Bukkit.getScheduler().runTask(plugin, task);
            return;
        }

        Bukkit.getGlobalRegionScheduler().execute(plugin, task);
    }

    public static void runAsync(Plugin plugin, Runnable task) {
        if (!FOLIA) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
            return;
        }

        Bukkit.getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
    }

    public static void runLater(Plugin plugin, Runnable task, long delayTicks) {
        if (!FOLIA) {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
            return;
        }

        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, scheduledTask -> task.run(), delayTicks);
    }

    public static void runTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (!FOLIA) {
            Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
            return;
        }

        Bukkit.getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                scheduledTask -> task.run(),
                delayTicks,
                periodTicks
        );
    }

    public static void runAtEntity(Plugin plugin, Entity entity, Runnable task) {
        if (!FOLIA) {
            Bukkit.getScheduler().runTask(plugin, task);
            return;
        }

        entity.getScheduler().run(plugin, scheduledTask -> task.run(), null);
    }

    public static void runAtLocation(Plugin plugin, Location location, Runnable task) {
        if (!FOLIA) {
            Bukkit.getScheduler().runTask(plugin, task);
            return;
        }

        Bukkit.getRegionScheduler().execute(
                plugin,
                location,
                task
        );
    }

    public static SchedulerTask runTimerAsync(
            Plugin plugin,
            Runnable task,
            long delayTicks,
            long periodTicks
    ) {
        if (!FOLIA) {
            BukkitTask bukkitTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                    plugin,
                    task,
                    delayTicks,
                    periodTicks
            );

            return bukkitTask::cancel;
        }

        ScheduledTask foliaTask = Bukkit.getAsyncScheduler().runAtFixedRate(
                plugin,
                scheduledTask -> task.run(),
                delayTicks * 50L,
                periodTicks * 50L,
                TimeUnit.MILLISECONDS
        );

        return foliaTask::cancel;
    }


    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }

    public interface SchedulerTask {
        void cancel();
    }
}

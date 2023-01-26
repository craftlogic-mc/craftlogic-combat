package ru.craftlogic.combat;

import com.google.common.base.MoreObjects;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketSoundEffect;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.LoaderState;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.craftlogic.api.CraftAPI;
import ru.craftlogic.api.event.player.PlayerTeleportHomeEvent;
import ru.craftlogic.api.event.player.PlayerTeleportReplyEvent;
import ru.craftlogic.api.event.player.PlayerTeleportRequestEvent;
import ru.craftlogic.api.event.player.PlayerTimedTeleportEvent;
import ru.craftlogic.api.server.PlayerManager;
import ru.craftlogic.api.server.Server;
import ru.craftlogic.api.text.Text;
import ru.craftlogic.api.util.ConfigurableManager;
import ru.craftlogic.api.world.Location;
import ru.craftlogic.api.world.OfflinePlayer;
import ru.craftlogic.api.world.Player;
import ru.craftlogic.combat.common.command.CommandDuel;
import ru.craftlogic.common.command.CommandManager;
import ru.craftlogic.regions.CraftRegions;
import ru.craftlogic.regions.common.event.RegionPvpStatusEvent;
import ru.craftlogic.warps.event.PlayerWarpEvent;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class CombatManager extends ConfigurableManager {
    private static final Logger LOGGER = LogManager.getLogger("combatManager");

    private final Map<String, CraftDuel> duels = new HashMap<>();
    private boolean enabled;
    private final Map<UUID, AtomicInteger> timers = new HashMap<>();

    public CombatManager(Server server, Path settingsDirectory) {
        super(server, settingsDirectory.resolve("combat.json"), LOGGER);
        MinecraftForge.EVENT_BUS.register(this);
    }

    public boolean isEnabled() {
        return enabled;
    }

    @Override
    protected String getModId() {
        return CraftCombat.MOD_ID;
    }

    @Override
    protected void load(JsonObject config) {
        enabled = JsonUtils.getBoolean(config, "enabled", false);
        JsonObject duels = JsonUtils.getJsonObject(config, "duels", new JsonObject());
        for (Map.Entry<String, JsonElement> entry : duels.entrySet()) {
            String id = entry.getKey();
            JsonObject value = (JsonObject) entry.getValue();
            this.duels.put(id, new CraftDuel(Integer.valueOf(id), value));
        }
    }

    @Override
    protected void save(JsonObject config) {
        config.addProperty("enabled", enabled);
        JsonObject duels = new JsonObject();
        for (Map.Entry<String, CraftDuel> entry : this.duels.entrySet()) {
            duels.add(entry.getKey(), entry.getValue().toJson());
        }
        config.add("duels", duels);
    }

    public CraftDuel getDuel(String id) {
        return duels.get(id);
    }

    public CraftDuel getDuel(EntityPlayer a, EntityPlayer b) {
        for (CraftDuel d : duels.values()) {
            if (d.hasParticipant(a.getGameProfile().getId()) && d.hasParticipant(b.getGameProfile().getId())) {
                return d;
            }
        }
        return null;
    }

    public CraftDuel getDuel(UUID user) {
        for (CraftDuel d : duels.values()) {
            if (d.hasParticipant(user)) {
                return d;
            }
        }
        return null;
    }

    public boolean isInDuel(UUID user) {
        return getDuel(user) != null;
    }

    public CraftDuel getFreeDuel() {
        for (Map.Entry<String, CraftDuel> entry : this.duels.entrySet()) {
            CraftDuel duel = entry.getValue();
            if (!duel.isOccupied(server.getPlayerManager())) {
                return duel;
            }
        }
        return null;
    }

    public CraftDuel createDuel(String id, Location location) throws IOException {
        CraftDuel duel = new CraftDuel(Integer.valueOf(id), location);
        duels.put(id, duel);
        save(true);
        return duel;
    }

    public CraftDuel deleteDuel(Integer id) throws IOException {
        CraftDuel duel = duels.remove(id.toString());
        if (duel != null) {
            save(true);
        }
        return duel;
    }

    @Override
    public void registerCommands(CommandManager commandManager) {
        if (server.isDedicated()) {
            commandManager.registerCommand(new CommandDuel());
        }
    }

    @SubscribeEvent
    @Optional.Method(modid = CraftRegions.MOD_ID)
    public void onPlayerPvp(RegionPvpStatusEvent event) {
        CraftDuel duel = getDuel(event.attacker, event.target);
        if (duel != null) {
            event.setResult(Event.Result.ALLOW);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onEntityAttack(AttackEntityEvent event) {
        if (!event.isCanceled()) {
            EntityPlayer player = event.getEntityPlayer();
            Entity target = event.getTarget();
            if (target instanceof EntityPlayer && !target.world.isRemote) {
                if (!player.capabilities.isCreativeMode) {
                    enterCombat(player);
                }
                enterCombat(((EntityPlayer) target));
            }
        }
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        EntityLivingBase living = event.getEntityLiving();
        if (living instanceof EntityPlayerMP) {
            PlayerManager manager = server.getPlayerManager();
            OfflinePlayer loser = manager.getOffline(((EntityPlayerMP) living).getGameProfile().getId());
            UUID id = loser.getId();
            Player player = manager.getOnline(id);
            if (player != null) {
                exitCombat(player);
                timers.remove(id);
            }
            CraftDuel duel = getDuel(id);
            if (duel != null) {
                UUID winnerId = duel.finish(id);
                if (winnerId != null) {
                    Player winner = server.getPlayerManager().getOnline(winnerId);
                    if (winner != null) {
                        timers.remove(winnerId);
                        exitCombat(winner);
                        server.broadcast(Text.translation("chat.duel.winner").yellow()
                            .arg(winner.getName(), Text::gold)
                            .arg(loser.getName(), Text::yellow));
                        Text<?, ?> toast = Text.translation("tooltip.win_duel");
                        Location bedLocation = winner.getBedLocation();
                        Location spawnLocation = winner.getWorld().getSpawnLocation();
                        winner.teleportDelayed(server -> duel.clear(), "duel", toast,
                            MoreObjects.firstNonNull(bedLocation, spawnLocation), 15, true);
                    }

                }
            }
        }
    }

    public void enterCombat(EntityPlayer player) {
        if (!player.capabilities.disableDamage) {
            UUID id = player.getGameProfile().getId();
            if (isInDuel(id)) {
                return;
            }
            if (timers.put(id, new AtomicInteger(300)) == null) {
                TextComponentTranslation message = new TextComponentTranslation("tooltip.combat.enter");
                message.getStyle().setColor(TextFormatting.YELLOW);
                player.sendStatusMessage(message, true);
                playSound((EntityPlayerMP) player, CraftCombat.SOUND_ENTER, 0.8F, 1F);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerExit(PlayerEvent.PlayerLoggedOutEvent event) {
        EntityPlayer player = event.player;
        if (!player.world.isRemote) {
            if (isInCombat(player.getGameProfile().getId())) {
                playSound((EntityPlayerMP) player, CraftCombat.SOUND_ENTER, 0.8F, 1F);
                player.attackEntityFrom(CraftCombat.PUNISHMENT, Float.MAX_VALUE);
            }
        }
    }

    @SubscribeEvent
    public void serverTickEvent(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            PlayerManager playerManager = server.getPlayerManager();
            Iterator<Map.Entry<UUID, AtomicInteger>> iterator = timers.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, AtomicInteger> entry = iterator.next();
                Player player = playerManager.getOnline(entry.getKey());
                if (player != null) {
                    AtomicInteger timer = entry.getValue();
                    CraftDuel duel = getDuel(entry.getKey());
                    if (duel == null || !duel.isOccupied(playerManager)) {
                        if (timer.decrementAndGet() <= 0) {
                            iterator.remove();
                            exitCombat(player);
                        }
                    }
                } else {
                    iterator.remove();
                }
            }
        }
    }

    private void exitCombat(Player player) {
        TextComponentTranslation message = new TextComponentTranslation("tooltip.combat.exit");
        message.getStyle().setColor(TextFormatting.YELLOW);
        player.sendStatus(message);
        player.playSound(CraftCombat.SOUND_EXIT, 0.8F, 1F);
    }

    public boolean isInCombat(UUID id) {
        boolean serverRunning = Loader.instance().getLoaderState() == LoaderState.SERVER_STARTED;
        AtomicInteger timer = timers.get(id);
        return serverRunning && timer != null && timer.get() > 0;
    }

    private void playSound(EntityPlayerMP player, SoundEvent sound, float volume, float pitch) {
        player.connection.sendPacket(new SPacketSoundEffect(sound, SoundCategory.PLAYERS, player.posX, player.posY, player.posZ, volume, pitch));
    }

    @SubscribeEvent
    @Optional.Method(modid = CraftAPI.MOD_ID + "-warps")
    public void onWarp(PlayerWarpEvent event) {
        checkTeleport(event, event.player);
    }

    @SubscribeEvent
    public void onHomeTeleport(PlayerTeleportHomeEvent event) {
        checkTeleport(event, event.player);
    }

    @SubscribeEvent
    public void onTeleportRequest(PlayerTeleportRequestEvent event) {
        Player target = event.target;
        Player player = event.player;
        if (isInDuel(target.getId()) || isInDuel(player.getId())) {
            event.setCanceled(true);
        }
        checkTeleport(event, event.player);
    }

    @SubscribeEvent
    public void onTeleportReply(PlayerTeleportReplyEvent event) {
        if (event.targetAccepted) {
            checkTeleport(event, event.player);
            checkTeleport(event, event.target);
        }
    }

    @SubscribeEvent
    public void onTimedTeleport(PlayerTimedTeleportEvent event) {
        checkTeleport(event, event.player);
    }

    private void checkTeleport(net.minecraftforge.event.entity.player.PlayerEvent event, Player player) {
        if (isInCombat(player.getId())) {
            event.setCanceled(true);
            player.sendMessage(Text.translation("chat.combat.no-teleport").red());
            player.playSound(CraftCombat.SOUND_WARN, 0.8F, 1F);
        }
    }
}

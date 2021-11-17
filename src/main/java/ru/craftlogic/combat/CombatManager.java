package ru.craftlogic.combat;

import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketSoundEffect;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.fml.common.Optional;
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
import ru.craftlogic.api.world.Player;
import ru.craftlogic.common.command.CommandManager;
import ru.craftlogic.warps.event.PlayerWarpEvent;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class CombatManager extends ConfigurableManager {
    private static final Logger LOGGER = LogManager.getLogger("combatManager");

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

    }

    @Override
    protected void save(JsonObject config) {
        config.addProperty("enabled", enabled);

    }

    @Override
    public void registerCommands(CommandManager commandManager) {
        if (server.isDedicated()) {

        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onEntityAttack(AttackEntityEvent event) {
        if (!event.isCanceled()) {
            EntityPlayer player = event.getEntityPlayer();
            Entity target = event.getTarget();
            if (target instanceof EntityPlayer && !target.world.isRemote) {
                enterCombat(player);
                enterCombat(((EntityPlayer) target));
            }
        }
    }

    private void enterCombat(EntityPlayer player) {
        if (!player.capabilities.disableDamage) {
            UUID id = player.getGameProfile().getId();
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
                    if (timer.decrementAndGet() <= 0) {
                        iterator.remove();
                        exitCombat(player);
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

    private boolean isInCombat(UUID id) {
        AtomicInteger timer = timers.get(id);
        return timer != null && timer.get() > 0;
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

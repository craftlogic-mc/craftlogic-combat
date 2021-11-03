package ru.craftlogic.combat;

import com.google.gson.JsonObject;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketSoundEffect;
import net.minecraft.util.CombatTracker;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Optional;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.craftlogic.api.CraftAPI;
import ru.craftlogic.api.event.player.PlayerEnterCombat;
import ru.craftlogic.api.event.player.PlayerExitCombat;
import ru.craftlogic.api.event.player.PlayerTeleportHomeEvent;
import ru.craftlogic.api.event.player.PlayerTeleportRequestEvent;
import ru.craftlogic.api.server.Server;
import ru.craftlogic.api.text.Text;
import ru.craftlogic.api.util.ConfigurableManager;
import ru.craftlogic.api.world.Player;
import ru.craftlogic.common.command.CommandManager;
import ru.craftlogic.warps.event.PlayerWarpEvent;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class CombatManager extends ConfigurableManager {
    private static final Logger LOGGER = LogManager.getLogger("combatManager");

    private boolean enabled;
    private final Set<UUID> inCombat = new HashSet<>();

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

    @SubscribeEvent
    public void onPlayerEnterCombat(PlayerEnterCombat event) {
        EntityPlayer player = event.getEntityPlayer();
        CombatTracker tracker = player.getCombatTracker();
        EntityLivingBase attacker = tracker.getBestAttacker();
        if (attacker instanceof EntityPlayer && attacker != player) {
            EntityPlayer p = (EntityPlayer) attacker;
            enterCombat(player);
            enterCombat(p);
        }
    }

    private void enterCombat(EntityPlayer player) {
        if (inCombat.add(player.getGameProfile().getId())) {
            TextComponentTranslation message = new TextComponentTranslation("tooltip.combat.enter");
            message.getStyle().setColor(TextFormatting.YELLOW);
            player.sendStatusMessage(message, true);
            playSound((EntityPlayerMP) player, CraftCombat.SOUND_ENTER, 0.8F, 1F);
        }
    }

    @SubscribeEvent
    public void onPlayerExitCombat(PlayerExitCombat event) {
        EntityPlayer player = event.getEntityPlayer();
        CombatTracker tracker = player.getCombatTracker();
        EntityLivingBase attacker = tracker.getBestAttacker();
        if (attacker instanceof EntityPlayerMP && attacker != player) {
            EntityPlayer p = (EntityPlayer) attacker;
            if (inCombat.remove(p.getGameProfile().getId())) {
                TextComponentTranslation message = new TextComponentTranslation("tooltip.combat.exit");
                message.getStyle().setColor(TextFormatting.YELLOW);
                p.sendStatusMessage(message, true);
                playSound((EntityPlayerMP) player, CraftCombat.SOUND_EXIT, 0.8F, 1F);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerExit(PlayerEvent.PlayerLoggedOutEvent event) {
        EntityPlayer player = event.player;
        if (!player.world.isRemote && inCombat.remove(player.getGameProfile().getId())) {
            CombatTracker tracker = player.getCombatTracker();
            EntityLivingBase attacker = tracker.getBestAttacker();
            if (attacker instanceof EntityPlayerMP && attacker != player) {
                playSound((EntityPlayerMP) player, CraftCombat.SOUND_ENTER, 0.8F, 1F);
                player.attackEntityFrom(CraftCombat.PUNISHMENT, Float.MAX_VALUE);
            }
        }
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

    private void checkTeleport(net.minecraftforge.event.entity.player.PlayerEvent event, Player player) {
        if (inCombat.contains(player.getId())) {
            event.setCanceled(true);
            player.sendMessage(Text.translation("chat.combat.no-teleport").red());
            playSound((EntityPlayerMP) event.getEntityPlayer(), CraftCombat.SOUND_WARN, 0.8F, 1F);
        }
    }
}

package ru.craftlogic.combat.common;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketSoundEffect;
import net.minecraft.util.CombatTracker;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import ru.craftlogic.api.event.player.PlayerEnterCombat;
import ru.craftlogic.api.event.server.ServerAddManagersEvent;
import ru.craftlogic.api.network.AdvancedMessageHandler;
import ru.craftlogic.combat.CombatManager;
import ru.craftlogic.combat.CraftCombat;
import ru.craftlogic.util.ReflectiveUsage;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@ReflectiveUsage
public class ProxyCommon extends AdvancedMessageHandler {

    public void preInit() {

    }

    public void init() {

    }

    public void postInit() {

    }

    @SubscribeEvent
    public void onServerAddManagers(ServerAddManagersEvent event) {
        event.addManager(CombatManager.class, CombatManager::new);
    }
}

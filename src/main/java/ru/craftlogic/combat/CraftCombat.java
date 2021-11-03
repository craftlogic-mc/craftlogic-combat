package ru.craftlogic.combat;


import net.minecraft.util.DamageSource;
import net.minecraft.util.SoundEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import ru.craftlogic.api.CraftAPI;
import ru.craftlogic.api.CraftSounds;
import ru.craftlogic.api.network.AdvancedNetwork;
import ru.craftlogic.combat.common.ProxyCommon;

@Mod(modid = CraftCombat.MOD_ID, version = CraftCombat.VERSION, dependencies = "required-after:" + CraftAPI.MOD_ID)
public class CraftCombat {
    public static final String MOD_ID = CraftAPI.MOD_ID + "-combat";
    public static final String VERSION = "0.2.0-BETA";

    @SidedProxy(clientSide = "ru.craftlogic.combat.client.ProxyClient", serverSide = "ru.craftlogic.combat.common.ProxyCommon")
    public static ProxyCommon PROXY;
    public static final AdvancedNetwork NETWORK = new AdvancedNetwork(MOD_ID);

    public static final DamageSource PUNISHMENT = new DamageSource("combat_punishment").setDamageBypassesArmor();
    public static SoundEvent SOUND_ENTER;
    public static SoundEvent SOUND_EXIT;
    public static SoundEvent SOUND_LOST;
    public static SoundEvent SOUND_WARN;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(PROXY);
        PROXY.preInit();
        SOUND_ENTER = CraftSounds.registerSound("combat.enter");
        SOUND_EXIT = CraftSounds.registerSound("combat.exit");
        SOUND_LOST = CraftSounds.registerSound("combat.lost");
        SOUND_WARN = CraftSounds.registerSound("combat.warn");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        NETWORK.openChannel();
        PROXY.init();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        PROXY.postInit();
    }
}

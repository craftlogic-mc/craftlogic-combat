package ru.craftlogic.combat.common.command;

import net.minecraft.command.CommandException;
import net.minecraftforge.common.MinecraftForge;
import ru.craftlogic.api.command.CommandBase;
import ru.craftlogic.api.command.CommandContext;
import ru.craftlogic.api.event.player.PlayerTeleportReplyEvent;
import ru.craftlogic.api.event.player.PlayerTeleportRequestEvent;
import ru.craftlogic.api.text.Text;
import ru.craftlogic.api.world.Location;
import ru.craftlogic.api.world.Player;
import ru.craftlogic.combat.CombatManager;
import ru.craftlogic.combat.Duel;

public class CommandInviteDuel extends CommandBase {
    public CommandInviteDuel() {
        super("pvp", 0,
            "<target:Player>");
    }

    @Override
    protected void execute(CommandContext ctx) throws CommandException {
        Player sender = ctx.senderAsPlayer();
        Player target = ctx.get("target").asPlayer();
        if (sender == target) {
            throw new CommandException("commands.request_duel.self");
        }
        CombatManager manager = ctx.server().getManager(CombatManager.class);
        if (manager.isInDuel(sender.getId()) || manager.isInDuel(target.getId())) {
            throw new CommandException("commands.duel_already");
        }
        Duel duel = manager.getFreeDuel();
        if (duel == null) {
            throw new CommandException("commands.duel_filled");
        }
        if (target.hasQuestion("duel")) {
            throw new CommandException("commands.request_duel.pending", target.getName());
        } else if (!MinecraftForge.EVENT_BUS.post(new PlayerTeleportRequestEvent(sender, target, ctx))) {
            Text<?, ?> title = Text.translation("commands.request_duel.question").arg(sender.getName());
            target.sendToastQuestion("duel", title, 0x404040, 30, accepted -> {
                if (sender.isOnline() && target.isOnline()) {
                    if (!MinecraftForge.EVENT_BUS.post(new PlayerTeleportReplyEvent(sender, target, accepted, ctx))) {
                        if (accepted) {
                            if (manager.getDuel(sender.getId()) != null || manager.getDuel(target.getId()) != null) {
                                sender.sendMessage(Text.translation(new CommandException("commands.duel_already")));
                                return;
                            }
                            Location location = duel.getLocation();
                            Text<?, ?> message = Text.translation("commands.request_duel.accepted").gold();
                            sender.sendMessage(message);
                            target.sendMessage(message);
                            Text<?, ?> toast = Text.translation("tooltip.request_duel");
                            duel.begin(sender.getId(), target.getId());
                            sender.teleportDelayed(server -> {}, "duel", toast, location, 5, true);
                            target.teleportDelayed(server -> {}, "duel", toast, location, 5, true);
                            manager.enterCombat(sender.getEntity());
                            manager.enterCombat(target.getEntity());

                        } else {
                            Text<?, ?> message = Text.translation("commands.request_teleport.declined").red();
                            sender.sendMessage(message);
                            target.sendMessage(message);
                        }
                    }
                }
            });
        }
    }
}

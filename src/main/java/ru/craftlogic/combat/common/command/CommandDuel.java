package ru.craftlogic.combat.common.command;

import net.minecraft.command.CommandException;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.common.MinecraftForge;
import ru.craftlogic.api.command.CommandBase;
import ru.craftlogic.api.command.CommandContext;
import ru.craftlogic.api.event.player.PlayerTeleportReplyEvent;
import ru.craftlogic.api.event.player.PlayerTeleportRequestEvent;
import ru.craftlogic.api.text.Text;
import ru.craftlogic.api.world.Location;
import ru.craftlogic.api.world.Player;
import ru.craftlogic.combat.CombatManager;
import ru.craftlogic.combat.CraftDuel;

import java.io.IOException;

public class CommandDuel extends CommandBase {
    public CommandDuel() {
        super("duel", 1,
            "<target:Player>",
            "<id:Duel> create|delete");
    }

    @Override
    protected void execute(CommandContext ctx) throws Exception {
        CombatManager duelManager = ctx.server().getManager(CombatManager.class);
        if (!duelManager.isEnabled()) {
            throw new CommandException("commands.warp.disabled");
        }

        Player player = ctx.senderAsPlayer();
        if (ctx.hasAction(0)) {
            String id = ctx.get("id").asString();
            switch (ctx.action(0)) {
                case "create": {
                    if (ctx.checkPermission(true, "commands.duel.create", 1)) {
                        if (duelManager.getDuel(id) != null) {
                            throw new CommandException("commands.warp.create.exists", id);
                        }
                        CraftDuel duel = duelManager.createDuel(id, player.getLocation());
                        if (duel != null) {
                            ctx.sendNotification(
                                Text.translation("commands.warp.create.success").green()
                                    .arg(id, Text::darkGreen)
                            );
                        }
                    }
                    break;
                }
                case "delete": {
                    CraftDuel duel = duelManager.getDuel(id);
                    if (duel == null) {
                        throw new CommandException("commands.warp.not-found", id);
                    }
                    if (ctx.checkPermission(true, "commands.duel.delete", 1)) {
                        ctx.sender().sendQuestionIfPlayer("delete-warp", new TextComponentTranslation("commands.warp.delete.question", id), 60, choice -> {
                            if (choice) {
                                try {
                                    if (duelManager.deleteDuel(duel.getId()) == duel) {
                                        ctx.sendNotification(
                                            Text.translation("commands.warp.delete.success").yellow()
                                                .arg(id, Text::gold)
                                        );
                                    }
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        });
                    }
                    break;
                }
            }
        } else {
            if (ctx.has("target")) {
                Player sender = ctx.senderAsPlayer();
                Player target = ctx.get("target").asPlayer();
                if (sender == target) {
                    throw new CommandException("commands.request_duel.self");
                }
                CombatManager manager = ctx.server().getManager(CombatManager.class);
                if (manager.isInDuel(sender.getId()) || manager.isInDuel(target.getId())) {
                    throw new CommandException("commands.duel_already");
                }
                CraftDuel duel = manager.getFreeDuel();
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
                                    duel.begin(sender.getId(), target.getId());
                                    sender.teleport(location);
                                    target.teleport(location);
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
    }
}

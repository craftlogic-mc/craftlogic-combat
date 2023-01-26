package ru.craftlogic.combat.common.command;

import net.minecraft.command.CommandException;
import net.minecraft.util.text.TextComponentTranslation;
import ru.craftlogic.api.command.CommandBase;
import ru.craftlogic.api.command.CommandContext;
import ru.craftlogic.api.text.Text;
import ru.craftlogic.api.world.Player;
import ru.craftlogic.combat.CombatManager;
import ru.craftlogic.combat.CraftDuel;

import java.io.IOException;

public class CommandWarpDuel extends CommandBase {
    public CommandWarpDuel() {
        super("duel", 1,
            "<id:Duel> create|delete");
    }

    @Override
    protected void execute(CommandContext ctx) throws Exception {
        CombatManager duelManager = ctx.server().getManager(CombatManager.class);
        if (!duelManager.isEnabled()) {
            throw new CommandException("commands.warp.disabled");
        }

        String id = ctx.get("id").asString();
        if (ctx.hasAction(0)) {
            switch (ctx.action(0)) {
                case "create": {
                    Player player = ctx.senderAsPlayer();
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
        }
    }
}

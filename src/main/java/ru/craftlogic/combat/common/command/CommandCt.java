package ru.craftlogic.combat.common.command;

import ru.craftlogic.api.command.CommandBase;
import ru.craftlogic.api.command.CommandContext;
import ru.craftlogic.api.text.Text;
import ru.craftlogic.api.world.Player;
import ru.craftlogic.combat.CombatManager;

import java.util.concurrent.atomic.AtomicInteger;

public class CommandCt extends CommandBase {
    public CommandCt() {
        super("ct", 0, "");
    }

    @Override
    protected void execute(CommandContext ctx) throws Exception {
        CombatManager duelManager = ctx.server().getManager(CombatManager.class);
        Player player = ctx.senderAsPlayer();
        AtomicInteger timer = duelManager.getTimer(player.getId());
        if (timer == null) {
            player.sendMessage(Text.translation("command.none_ct").yellow());
        } else {
            int i = timer.get() / 20;
            player.sendMessage(Text.translation("command.now_ct").arg(i).gold());
        }
    }
}

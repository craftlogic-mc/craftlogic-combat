package ru.craftlogic.combat;

import com.google.gson.JsonObject;
import net.minecraft.util.JsonUtils;
import ru.craftlogic.api.server.PlayerManager;
import ru.craftlogic.api.world.Location;

import java.util.UUID;

public class CraftDuel {
    private final int id;
    private final Location location;
    private UUID sender, target;

    public CraftDuel(int id, JsonObject data) {
        this(id, Location.deserialize(JsonUtils.getInt(data, "dim", 0),
            JsonUtils.getJsonObject(data, "loc")));

    }

    public CraftDuel(int id, Location location) {
        this.id = id;
        this.location = location;
    }

    public int getId() {
        return id;
    }

    public Location getLocation() {
        return location;
    }

    public boolean isOccupied(PlayerManager manager) {
        return sender != null && manager.getOnline(sender) != null || target != null && manager.getOnline(target) != null;
    }

    public void begin(UUID a, UUID b) {
        sender = a;
        target = b;
    }

    public UUID finish(UUID loser) {
        if (sender == loser) {
            sender = null;
            return target;
        } else if (target == loser) {
            target = null;
            return sender;
        }
        return null;
    }

    public void clear() {
        sender = null;
        target = null;
    }

    public boolean hasParticipant(UUID user) {
        return sender == user || target == user;
    }


    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("dim", location.getDimensionId());
        obj.add("loc", location.serialize());
        return obj;
    }
}

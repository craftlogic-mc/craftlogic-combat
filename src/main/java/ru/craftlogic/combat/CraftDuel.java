package ru.craftlogic.combat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.JsonUtils;
import ru.craftlogic.api.server.PlayerManager;
import ru.craftlogic.api.util.Pair;
import ru.craftlogic.api.world.Location;
import ru.craftlogic.api.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class CraftDuel {
    private final int id;
    private final List<Location> location;
    private UUID sender, target;

    public CraftDuel(int id, JsonObject data) {
        this(id, locations(JsonUtils.getJsonArray(data, "loc")));
    }

    public CraftDuel(int id, List<Location> location) {
        this.id = id;
        this.location = location;
    }

    private static List<Location> locations(JsonArray array) {
        List<Location> result = new ArrayList<>(array.size());
        for (JsonElement e : array) {
            JsonObject o = e.getAsJsonObject();
            int dim = JsonUtils.getInt(o, "dim");
            result.add(Location.deserialize(dim, o));
        }
        return result;

    }

    public int getId() {
        return id;
    }

    public Pair<Location, Location> getLocation() {
        int size = location.size();
        if (size < 2) {
            return null;
        }
        Random random = new Random();
        int a = random.nextInt(size);
        int b;
        while ((b = random.nextInt(size)) == a) {}
        return Pair.of(location.get(a), location.get(b));
    }

    public boolean hasLocations(World world) {
        int count = 0;
        for (Location l : location) {
            if (l.getDimension() == world.getDimension()) {
                count++;
            }
        }
        return count > 1;
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
        JsonArray loc = new JsonArray();
        for (Location l : location) {
            JsonObject o = l.serialize();
            o.addProperty("dim", l.getDimensionId());
            loc.add(o);
        }
        obj.add("loc", loc);
        return obj;
    }

    public void addLocation(Location location) {
        this.location.add(location);
    }
}

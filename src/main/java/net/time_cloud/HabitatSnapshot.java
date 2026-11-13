package net.time_cloud;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class HabitatSnapshot {
    public static final int MAX_HABITATS = 512;
    public static final HabitatSnapshot EMPTY = new HabitatSnapshot(List.of());
    private final List<Habitat> habitats;
    private final Map<String, List<Habitat>> byDimension;
    private final String revision;

    public HabitatSnapshot(Collection<Habitat> entries) {
        if (entries.size() > MAX_HABITATS) throw new IllegalArgumentException("最多支持 " + MAX_HABITATS + " 个区域");
        List<Habitat> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing(Habitat::id));
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < sorted.size(); i++) {
            Habitat a = sorted.get(i);
            if (!ids.add(a.id())) throw new IllegalArgumentException("区域名称重复：" + a.id());
            for (int j = 0; j < i; j++) {
                Habitat b = sorted.get(j);
                if (a.enabled() && b.enabled() && a.overlaps(b))
                    throw new IllegalArgumentException("启用的区域不能重叠：" + a.id() + " / " + b.id());
            }
        }
        habitats = List.copyOf(sorted);
        Map<String, List<Habitat>> map = new HashMap<>();
        for (Habitat habitat : habitats) if (habitat.enabled())
            map.computeIfAbsent(habitat.dimension(), ignored -> new ArrayList<>()).add(habitat);
        Map<String, List<Habitat>> immutable = new HashMap<>();
        map.forEach((key, value) -> immutable.put(key, List.copyOf(value)));
        byDimension = Map.copyOf(immutable);
        try {
            revision = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(physicsJson().toString().getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) { throw new AssertionError(exception); }
    }

    public List<Habitat> habitats() { return habitats; }
    public String revision() { return revision; }
    public Habitat named(String name) {
        String normalized = Habitat.normalizeId(name);
        return habitats.stream().filter(habitat -> habitat.id().equals(normalized)).findFirst().orElse(null);
    }
    public Habitat at(String dimension, double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return null;
        for (Habitat habitat : byDimension.getOrDefault(dimension, List.of()))
            if (habitat.contains(x, y, z)) return habitat;
        return null;
    }
    public Habitat gravityAt(String dimension,double x,double y,double z) {
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))return null;
        for(Habitat habitat:byDimension.getOrDefault(dimension,List.of()))if(habitat.containsGravity(x,y,z))return habitat;
        return null;
    }
    public JsonObject physicsJson() {
        JsonObject object=new JsonObject();object.addProperty("schema_version",2);
        JsonArray entries=new JsonArray();habitats.forEach(h->entries.add(h.withoutCore().json()));object.add("habitats",entries);return object;
    }
    public JsonObject json() {
        JsonObject object = new JsonObject(); object.addProperty("schema_version", 2);
        JsonArray entries = new JsonArray(); habitats.forEach(habitat -> entries.add(habitat.json()));
        object.add("habitats", entries); return object;
    }
}

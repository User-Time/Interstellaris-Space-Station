package net.time_cloud;

import com.google.gson.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class HabitatStore {
    private static final long MAX_BYTES = 1024 * 1024;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path path;
    private final java.util.function.Predicate<Habitat> allowed;
    private volatile HabitatSnapshot current = HabitatSnapshot.EMPTY;
    private volatile String writeLock;
    public void lockWrites(String reason){writeLock=reason;}
    public boolean writable(){return writeLock==null;}

    public HabitatStore(Path path) { this(path, habitat -> true); }
    public HabitatStore(Path path, java.util.function.Predicate<Habitat> allowed) {
        this.path = path; this.allowed = allowed;
    }
    public Path path() { return path; }
    public HabitatSnapshot snapshot() { return current; }

    public synchronized HabitatSnapshot load() throws IOException {
        if (!Files.exists(path)) return save(HabitatSnapshot.EMPTY);
        if (Files.size(path) > MAX_BYTES) throw new IOException("配置文件超过 1 MiB");
        JsonObject object = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        if (!Set.of("1","2").contains(object.get("schema_version").getAsString()))
            throw new IllegalArgumentException("不支持的 schema_version");
        List<Habitat> habitats = new ArrayList<>();
        JsonArray entries = object.getAsJsonArray("habitats");
        if (entries.size() > HabitatSnapshot.MAX_HABITATS) throw new IllegalArgumentException("区域数量超过限制");
        for (JsonElement entry : entries) habitats.add(Habitat.parse(entry.getAsJsonObject()));
        HabitatSnapshot loaded = new HabitatSnapshot(habitats);
        validateDimensions(loaded);
        current = loaded;
        writeLock=null;
        return loaded;
    }

    /** Publish only after durable replacement; a failed write leaves the live snapshot unchanged. */
    public synchronized HabitatSnapshot save(HabitatSnapshot snapshot) throws IOException {
        if(writeLock!=null)throw new IOException(writeLock);
        validateDimensions(snapshot);
        Files.createDirectories(path.toAbsolutePath().getParent());
        byte[] bytes = (GSON.toJson(snapshot.json()) + "\n").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("配置文件超过 1 MiB");
        Path temp = Files.createTempFile(path.toAbsolutePath().getParent(), ".station-habitat-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            if (Files.exists(path)) Files.copy(path, path.resolveSibling(path.getFileName() + ".previous"),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            try { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException exception) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            current = snapshot;
            return snapshot;
        } finally { Files.deleteIfExists(temp); }
    }

    private void validateDimensions(HabitatSnapshot snapshot) {
        for (Habitat habitat : snapshot.habitats()) if (!allowed.test(habitat))
            throw new IllegalArgumentException("不支持的太空维度：" + habitat.dimension());
    }

    public HabitatSnapshot put(Habitat habitat, boolean replace) throws IOException {
        List<Habitat> copy = new ArrayList<>(current.habitats());
        Habitat old = current.named(habitat.id());
        if (old != null && !replace) throw new IllegalArgumentException("区域已存在：" + habitat.id());
        if (old == null && replace) throw new IllegalArgumentException("区域不存在：" + habitat.id());
        copy.removeIf(entry -> entry.id().equals(habitat.id())); copy.add(habitat);
        return save(new HabitatSnapshot(copy));
    }

    public HabitatSnapshot delete(String id) throws IOException {
        Habitat old = current.named(id);
        if (old == null) throw new IllegalArgumentException("区域不存在：" + id);
        List<Habitat> copy = new ArrayList<>(current.habitats()); copy.remove(old);
        return save(new HabitatSnapshot(copy));
    }
}

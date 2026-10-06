# The `servers.dat` fixtures

`servers-1.21.11.dat` and `servers-1.8.9.dat` are real files, each written by its own version's code. Each generator below makes the calls that version's `ServerList` save method makes, and nothing more:
- every entry's own serialiser;
- for 1.21.11, the `hidden` flag that `save()` adds;
- a list named `servers` in a root compound;
- the game's `NbtIo.write`.

The entries cover what the reader has to handle:
- an icon;
- a resource-pack choice (`acceptTextures`);
- an address with a port;
- a bracketed IPv6 address;
- non-ASCII characters in a name;
- on 1.21.11, a hidden entry.

## Regenerating

Compile and run each generator against the Loom-mapped game jar for its version from the client build's cache, plus that version's libraries from ash's depot. On Windows the classpath separator is `;`.

1.21.11 also needs its vanilla client jar on the classpath, last, so that `SharedConstants.tryDetectVersion()` can find `version.json`.

### 1.8.9 (Legacy Fabric's Yarn names)

```java
import java.io.File;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;

/** Writes servers.dat the way 1.8.9's ServerList.saveFile does: each entry's serialize(), in a list named "servers". */
public class Gen {
    public static void main(String[] args) throws Exception {
        ServerInfo hypixel = new ServerInfo("Hypixel", "mc.hypixel.net", false);
        // A 1x1 PNG, base64, as the game stores a server's icon.
        hypixel.setIcon("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==");
        ServerInfo local = new ServerInfo("Local test", "localhost:25570", false);
        local.setResourcePackState(ServerInfo.ResourcePackState.ENABLED);
        ServerInfo unicode = new ServerInfo("Spëcial ★ server", "[::1]:25565", false);

        NbtList list = new NbtList();
        for (ServerInfo s : new ServerInfo[] {hypixel, local, unicode}) {
            list.add(s.serialize());
        }
        NbtCompound root = new NbtCompound();
        root.put("servers", list);
        NbtIo.write(root, new File(args[0]));
    }
}
```

### 1.21.11 (Mojang's names)

```java
import java.nio.file.Path;
import java.util.Base64;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

/** Writes servers.dat the way 1.21.11's ServerList.save does: visible entries with hidden=false, then hidden ones with hidden=true. */
public class Gen {
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        ServerData hypixel = new ServerData("Hypixel", "mc.hypixel.net", ServerData.Type.OTHER);
        hypixel.setIconBytes(Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg=="));
        ServerData local = new ServerData("Local test", "localhost:25570", ServerData.Type.OTHER);
        local.setResourcePackStatus(ServerData.ServerPackStatus.ENABLED);
        ServerData unicode = new ServerData("Spëcial ★ server", "[::1]:25565", ServerData.Type.OTHER);
        ServerData hidden = new ServerData("Hidden one", "hidden.example.com", ServerData.Type.OTHER);

        ListTag list = new ListTag();
        for (ServerData s : new ServerData[] {hypixel, local, unicode}) {
            CompoundTag tag = s.write();
            tag.putBoolean("hidden", false);
            list.add(tag);
        }
        CompoundTag tag = hidden.write();
        tag.putBoolean("hidden", true);
        list.add(tag);

        CompoundTag root = new CompoundTag();
        root.put("servers", list);
        NbtIo.write(root, Path.of(args[0]));
    }
}
```

# fear-skin-agent

The launcher's own authlib patch. A small `java.lang.instrument` agent that runs
alongside authlib-injector and transforms one class:

    com.mojang.authlib.yggdrasil.YggdrasilMinecraftSessionService

It inserts a single call at the top of the profile lookup methods, passing the
`GameProfile` to `FearSkinBridge`. The bridge reads the name off that profile and posts it
to the launcher's local skin server.

## Why

On an `online-mode=false` server the client looks a player up by their **offline UUID** -
`UUID.nameUUIDFromBytes("OfflinePlayer:" + name)` - which is a hash and cannot be
reversed. The name is right there in the GameProfile the session service was handed; it
just never reaches the request, so the lookup comes back empty and the player renders as
Steve. Ely.by solves this by shipping a modified copy of authlib. This does the same job
without redistributing anyone's library, and the resolution happens on our own server,
which is where the CraftynMC skins live anyway.

## Fail-safe by design

If the class does not match, if neither lookup method is present, or if anything throws,
the transformer returns null and authlib is left exactly as it was. A version where
authlib moved things around costs you the feature, not the launch.

## Build

Needs a JDK and `asm-9.7.jar` (shaded in, so the game JVM needs nothing on its classpath).

    javac --release 8 -cp asm-9.7.jar -d build/classes $(find src -name '*.java')
    # unpack asm into build/classes, drop its module-info.class
    jar cfm fear-skin-agent.jar MANIFEST.MF -C build/classes .

The manifest must carry `Premain-Class: com.fear.skin.agent.FearSkinAgent`.

## Verified

Run against a stub of the target class, the transformer patches the class, both lookup
methods still return their original values, and the name reaches the endpoint:

    [FearSkinAgent] installed
    [FearSkinAgent] patched com/mojang/authlib/yggdrasil/YggdrasilMinecraftSessionService
    SERVER SAW: GET /fear/skin-note?uuid=069a79f444e94726a5befca90e38aaf5&name=Notch HTTP/1.1
    PASS

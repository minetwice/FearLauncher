# libs

Drop-in `.aar` files the launcher builds against.

## discord_partner_sdk.aar

The Discord Social SDK, downloaded from the Discord Developer Portal (Social SDK ->
Getting Started) for the Fearlauncher application. It is what publishes the Discord
presence: the launcher registers as an application and talks to the Discord app
installed on the phone, so no account linking and no user token is involved.

It is committed here rather than fetched in CI because Discord hands it out from the
portal rather than from a public URL. It is ~28 MB and carries native libraries for
arm64-v8a, armeabi-v7a, x86 and x86_64, plus a Prefab package, so the CMake side can
find_package(discord_partner_sdk) and link it directly.

Upload it into this folder as `discord_partner_sdk.aar`.

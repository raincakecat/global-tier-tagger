# Building

Install JDK 21 for Minecraft 1.21.11 and JDK 25 for the 26.x builds.
Set `JAVA_HOME_21_X64` and `JAVA_HOME_25_X64` to their installation folders.

On Windows:

```powershell
.\gradlew.bat :common:test releaseJars
```

The four JARs are written to `build/release/`.

To run a client test:

```powershell
.\gradlew.bat :mc12111:runClientGameTest
```

Other targets are `mc2612`, `mc262`, and `mc263`.
The [compatibility manifest](compatibility-versions.json) lists the Fabric
API versions used for each target. To run that matrix:

```powershell
python tools/verify-fabric-matrix.py --manifest docs/compatibility-versions.json
```

The 26.3 Gradle launcher includes Mojang's `-XX:StackShadowPages=32`
argument. Other launchers should retain the official Minecraft JVM arguments.

These instructions do not grant permission to redistribute builds or
modified versions. The [project license](../LICENSE) applies.

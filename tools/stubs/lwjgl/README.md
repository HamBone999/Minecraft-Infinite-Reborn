# LWJGL compile stubs

Recompiling a client class needs LWJGL on the classpath, and a headless build box does not
have it. These stubs satisfy `javac` and are never packaged — the real LWJGL is supplied by
the launcher at runtime.

> [!WARNING]
> The method descriptors must match the real LWJGL **exactly**. A wrong one compiles fine
> and then throws `NoSuchMethodError` in front of players. Do not hand-write them from
> memory — read them out of the shipped bytecode:
>
> ```sh
> javap -p -c TitleScreen.class | grep -oE 'org/lwjgl/[^ ]*\.[a-zA-Z0-9_]+:\([^)]*\)[A-Za-z;/]*' | sort -u
> ```
>
> Then add any missing signature to the stub verbatim.

```sh
javac -source 8 -target 8 -d stubs/lwjgl/classes $(find stubs/lwjgl/src -name '*.java')
javac -cp "Infinite.jar:stubs/lwjgl/classes" -d build MyScreen.java
```

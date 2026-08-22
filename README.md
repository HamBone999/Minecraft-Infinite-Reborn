# Minecraft Infinite Reborn
This repository is to maintain and update what was formally known as "Minecraft Infinite". I will be accepting bug reports. Below you will find some basic info and an install guide. Enjoy. 

Common problems:
* It is recommended to allocate at least 3GB of memory to the mod to ensure it runs smoothly. Technically it should work with only 1GB, but you may experience slowdown or crashes.
* If sound doesnt work for you, it is 100% an issue with your setup. Get the sounds from the [repo](https://github.com/VesuviusVenox/Classic-Resources) and play in offline mode to avoid redownloading them. Also, make sure you are NOT using sound proxies like Betacraft, otherwise the mod will try to fetch the sounds from said proxy and fail, since it expects to connect to my own sounds repo.
* Mac and Linux may experience mouse-related issues or other oddities with input methods. This is due to the mod using LWJGL 2.9.3/2.9.4, which has such issues on Linux and Mac. The only fix I know if is to update the mod to LWJGL 3, which I might do eventually.

How to install:
* For first time install, get the instance zip file (Infdev-1.0.7.zip) and drag-and-drop it (or import from zip) to your MultiMC/Prism launcher.
* For updating, get the latest version jar file from the archive, right-click your instance, go to the version tab, REMOVE the old jar and click "Add to jar", then select the downloaded jar file.
* IMPORTANT! Make sure you are using Java 8. Java version 21 and higher will cause the mod to crash due to some bullshit incompatability issues between newer Java versions and LWJGL 2.9
* For server instructions refer to the README included with those files.

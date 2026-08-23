# Minecraft Infinite Reborn

This repository is to maintain and update what was formally known as "Minecraft Infinite". I am building off of Trivaxy's Minecraft-Infinite-Multiplayer fork as its been overhauled. 
I will be accepting bug reports. Below you will find some basic info and an install guide. Enjoy. 

Common problems:
* It is recommended to allocate at least 3GB of memory to the mod to ensure it runs smoothly. Technically it should work with only 1GB, but you may experience slowdown or crashes.
* If sound doesnt work for you, it is 100% an issue with your setup. Get the sounds from the [repo](https://github.com/VesuviusVenox/Classic-Resources) and play in offline mode to avoid redownloading them. Also, make sure you are NOT using sound proxies like Betacraft, otherwise the mod will try to fetch the sounds from said proxy and fail, since it expects to connect to my own sounds repo.
* Mac and Linux may experience mouse-related issues or other oddities with input methods. This is due to the mod using LWJGL 2.9.3/2.9.4, which has such issues on Linux and Mac. The only fix I know if is to update the mod to LWJGL 3, which I might do eventually.

How to install:
* Download the latest files from [Releases](https://github.com/HamBone999/Minecraft-Infinite-Reborn/releases)
* For first time install, go to your default MC launcher, Prism, or MultiMC. Create an instance for a1.0.4, run it and make sure you get to title screen.
* Follow instructions in INSTALL.md

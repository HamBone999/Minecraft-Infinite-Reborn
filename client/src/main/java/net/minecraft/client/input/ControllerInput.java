package net.minecraft.client.input;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.options.KeyBind;
import net.minecraft.client.options.OptionsList;
import org.lwjgl.input.Controller;
import org.lwjgl.input.Controllers;

/**
 * Gamepad support.
 *
 * Buttons drive the existing key binds rather than the game's movement code directly, so a
 * controller behaves exactly like the keyboard everywhere -- including in mods, which never
 * learn a controller was involved. Sticks are the only analogue path: the left one presses the
 * movement binds past a deadzone, the right one feeds MouseHelper the same delta the mouse does.
 *
 * Everything here is wrapped. JInput is a native library and a missing, unplugged or unusual
 * device must degrade to "no controller", never take the game down.
 */
public final class ControllerInput {

   /** Bind names in the order the binding screen lists them. */
   public static final String[] BINDABLE = {
      "key.jump", "key.sneak", "key.sprint", "key.crawl",
      "key.attack", "key.use", "key.drop", "key.inventory",
      "key.itemPrev", "key.itemNext", "key.swap", "key.pickItem",
      "key.chat", "key.playerList", "key.pause", "key.thirdPerson",
   };

   private static final Map<String, Integer> BINDINGS = new LinkedHashMap<String, Integer>();
   private static final Map<String, Boolean> HELD = new LinkedHashMap<String, Boolean>();

   public static boolean enabled = true;
   public static boolean invertY = false;
   public static int lookSpeed = 5;      // 1-10
   public static int deadzonePct = 20;   // 0-60

   private static boolean initialised;
   private static boolean available;
   private static String deviceName = "";
   private static float lookX, lookY;
   private static File configFile;

   private ControllerInput() { }

   // ---------------------------------------------------------------- lifecycle

   /** Safe to call every frame; only the first call does anything. */
   private static void init() {
      if (initialised) {
         return;
      }
      initialised = true;
      try {
         if (!Controllers.isCreated()) {
            Controllers.create();
         }
         available = Controllers.getControllerCount() > 0;
         if (available) {
            deviceName = Controllers.getController(0).getName();
            System.out.println("[controller] using " + deviceName);
         } else {
            System.out.println("[controller] no device detected");
         }
      } catch (Throwable t) {
         available = false;
         System.out.println("[controller] unavailable: " + t);
      }
      load();
   }

   public static boolean isAvailable() {
      init();
      return available;
   }

   public static String deviceName() {
      init();
      return available ? deviceName : "";
   }

   /** Re-scan, for the binding screen's Detect button. */
   public static void redetect() {
      initialised = false;
      available = false;
      deviceName = "";
      init();
   }

   // ---------------------------------------------------------------- per frame

   /**
    * Called from MouseHelper.moveMouse, which GameRenderer runs every frame while playing.
    *
    * Deliberately NOT hooked into Minecraft.runTick: Minecraft.java is one of the classes that
    * does not survive a decompile/recompile round trip on this project -- it loses Minecraft$2 --
    * so it is left untouched.
    */
   public static void tick() {
      init();
      if (!available || !enabled) {
         lookX = lookY = 0.0F;
         return;
      }

      try {
         Controllers.poll();
         Controller pad = Controllers.getController(0);
         float dead = deadzonePct / 100.0F;

         // --- buttons -> binds
         int count = pad.getButtonCount();
         for (int i = 0; i < BINDABLE.length; i++) {
            String name = BINDABLE[i];
            Integer button = BINDINGS.get(name);
            boolean down = button != null && button.intValue() >= 0
                        && button.intValue() < count && pad.isButtonPressed(button.intValue());
            apply(name, down);
         }

         // --- left stick -> movement binds, as if they were keys
         float lx = axis(pad.getXAxisValue(), dead);
         float ly = axis(pad.getYAxisValue(), dead);
         apply("key.forward", ly < -0.5F);
         apply("key.back", ly > 0.5F);
         apply("key.left", lx < -0.5F);
         apply("key.right", lx > 0.5F);

         // --- right stick -> look, accumulated for MouseHelper
         float rx = axis(pad.getRXAxisValue(), dead);
         float ry = axis(pad.getRYAxisValue(), dead);
         float speed = lookSpeed * 2.0F;
         lookX = rx * speed;
         lookY = (invertY ? ry : -ry) * speed;
      } catch (Throwable t) {
         available = false;
         lookX = lookY = 0.0F;
         System.out.println("[controller] lost: " + t);
      }
   }

   /** Rescale past the deadzone so movement starts smoothly rather than jumping. */
   private static float axis(float raw, float dead) {
      float mag = Math.abs(raw);
      if (mag <= dead) {
         return 0.0F;
      }
      float scaled = (mag - dead) / (1.0F - dead);
      return raw < 0 ? -scaled : scaled;
   }

   /**
    * Drive a bind the way the keyboard does.
    *
    * `pressed` is the held state that movement reads every tick; `pressTime` is a queue of
    * discrete presses that pressed() consumes, which is what one-shot actions like dropping an
    * item use. Incrementing it only on the rising edge is what stops a held button from firing
    * an action once per frame.
    */
   private static void apply(String bindName, boolean down) {
      KeyBind bind = bindFor(bindName);
      if (bind == null) {
         return;
      }
      Boolean was = HELD.get(bindName);
      boolean previously = was != null && was.booleanValue();
      if (down && !previously) {
         bind.pressTime++;
      }
      if (down != previously) {
         bind.pressed = down;
      }
      HELD.put(bindName, Boolean.valueOf(down));
   }

   public static KeyBind bindFor(String name) {
      KeyBind[] all = OptionsList.allKeys;
      for (int i = 0; i < all.length; i++) {
         if (all[i] != null && name.equals(all[i].name)) {
            return all[i];
         }
      }
      return null;
   }

   /** Look delta since the last frame, consumed by MouseHelper. */
   public static int consumeLookX() {
      int v = Math.round(lookX);
      return v;
   }

   public static int consumeLookY() {
      int v = Math.round(lookY);
      return v;
   }

   // ---------------------------------------------------------------- binding

   public static int buttonFor(String bindName) {
      Integer b = BINDINGS.get(bindName);
      return b == null ? -1 : b.intValue();
   }

   public static void bind(String bindName, int button) {
      // one button drives one action, so clear any previous owner
      List<String> clash = new ArrayList<String>();
      for (Map.Entry<String, Integer> e : BINDINGS.entrySet()) {
         if (e.getValue() != null && e.getValue().intValue() == button && !e.getKey().equals(bindName)) {
            clash.add(e.getKey());
         }
      }
      for (int i = 0; i < clash.size(); i++) {
         BINDINGS.put(clash.get(i), Integer.valueOf(-1));
      }
      BINDINGS.put(bindName, Integer.valueOf(button));
      save();
   }

   public static void unbind(String bindName) {
      BINDINGS.put(bindName, Integer.valueOf(-1));
      save();
   }

   /** First button currently held, or -1. Used by the binding screen to capture a press. */
   public static int pressedButton() {
      if (!isAvailable()) {
         return -1;
      }
      try {
         Controllers.poll();
         Controller pad = Controllers.getController(0);
         for (int i = 0; i < pad.getButtonCount(); i++) {
            if (pad.isButtonPressed(i)) {
               return i;
            }
         }
      } catch (Throwable t) {
      }
      return -1;
   }

   public static void resetDefaults() {
      BINDINGS.clear();
      defaults();
      save();
   }

   /** A standard gamepad face layout. Everything here is remappable in the screen. */
   private static void defaults() {
      put("key.jump", 0);
      put("key.sneak", 1);
      put("key.drop", 2);
      put("key.inventory", 3);
      put("key.itemPrev", 4);
      put("key.itemNext", 5);
      put("key.playerList", 6);
      put("key.pause", 7);
      put("key.sprint", 8);
      put("key.crawl", 9);
      for (int i = 0; i < BINDABLE.length; i++) {
         if (!BINDINGS.containsKey(BINDABLE[i])) {
            BINDINGS.put(BINDABLE[i], Integer.valueOf(-1));
         }
      }
   }

   private static void put(String name, int button) {
      BINDINGS.put(name, Integer.valueOf(button));
   }

   // ---------------------------------------------------------------- config

   private static File file() {
      if (configFile == null) {
         configFile = new File(Minecraft.getMinecraftDir(), "controller.properties");
      }
      return configFile;
   }

   public static void load() {
      defaults();
      InputStream in = null;
      try {
         File f = file();
         if (!f.isFile()) {
            save();
            return;
         }
         Properties p = new Properties();
         in = new FileInputStream(f);
         p.load(in);
         enabled = bool(p, "enabled", enabled);
         invertY = bool(p, "invert-y", invertY);
         lookSpeed = clamp(integer(p, "look-speed", lookSpeed), 1, 10);
         deadzonePct = clamp(integer(p, "deadzone-percent", deadzonePct), 0, 60);
         for (int i = 0; i < BINDABLE.length; i++) {
            String key = "bind." + BINDABLE[i];
            if (p.getProperty(key) != null) {
               BINDINGS.put(BINDABLE[i], Integer.valueOf(integer(p, key, -1)));
            }
         }
      } catch (Throwable t) {
         System.out.println("[controller] could not read " + file() + ": " + t);
      } finally {
         close(in);
      }
   }

   public static void save() {
      OutputStream out = null;
      try {
         Properties p = new Properties();
         p.setProperty("enabled", Boolean.toString(enabled));
         p.setProperty("invert-y", Boolean.toString(invertY));
         p.setProperty("look-speed", Integer.toString(lookSpeed));
         p.setProperty("deadzone-percent", Integer.toString(deadzonePct));
         for (int i = 0; i < BINDABLE.length; i++) {
            p.setProperty("bind." + BINDABLE[i], Integer.toString(buttonFor(BINDABLE[i])));
         }
         out = new FileOutputStream(file());
         p.store(out, "Minecraft Infinite Reborn -- controller. Button numbers, -1 is unbound.");
      } catch (Throwable t) {
         System.out.println("[controller] could not write " + file() + ": " + t);
      } finally {
         close(out);
      }
   }

   private static void close(Object stream) {
      try {
         if (stream instanceof InputStream) {
            ((InputStream)stream).close();
         } else if (stream instanceof OutputStream) {
            ((OutputStream)stream).close();
         }
      } catch (Throwable t) {
      }
   }

   private static boolean bool(Properties p, String key, boolean def) {
      String v = p.getProperty(key);
      return v == null ? def : Boolean.parseBoolean(v.trim());
   }

   private static int integer(Properties p, String key, int def) {
      String v = p.getProperty(key);
      if (v == null) {
         return def;
      }
      try {
         return Integer.parseInt(v.trim());
      } catch (NumberFormatException e) {
         return def;
      }
   }

   private static int clamp(int v, int lo, int hi) {
      return v < lo ? lo : (v > hi ? hi : v);
   }
}

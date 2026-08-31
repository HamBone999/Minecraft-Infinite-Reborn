package net.minecraft.commands.custom;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.game.block.Block;
import net.minecraft.game.item.Item;
import net.minecraft.game.item.enchantment.Enchantment;
import net.minecraft.game.item.potion.Effect;

/**
 * Friendly names for blocks, items, effects and enchantments.
 *
 * The registries are the vanilla {@code *List} classes, whose fields are already named the way
 * a player would say them, so these maps are built by reflection instead of by hand. A block
 * added upstream becomes spellable here the day it lands, with nothing to keep in step.
 *
 * Lookup ignores case and punctuation, so {@code waterMoving}, {@code water_moving} and
 * {@code WATERMOVING} are the same name. A bare number is always accepted as a raw id.
 */
public final class Names {

   private static Map<String, Integer> blocks;
   private static Map<String, Integer> items;
   private static Map<String, Effect> effects;
   private static Map<String, Enchantment> enchants;

   private Names() {
   }

   /** Lowercase, letters and digits only, so punctuation never decides whether a name matches. */
   public static String key(String s) {
      StringBuilder b = new StringBuilder(s.length());
      for (int i = 0; i < s.length(); i++) {
         char ch = s.charAt(i);
         if (Character.isLetterOrDigit(ch)) {
            b.append(Character.toLowerCase(ch));
         }
      }

      return b.toString();
   }

   // ---- registries -----------------------------------------------------------

   /**
    * Reads every public static field of {@code owner} whose value is a {@code type}, keyed by
    * field name. A field that is null at class-init time is skipped rather than fatal: the
    * registries are populated by a static block, and a partially built one still answers for
    * everything it did manage to fill in.
    */
   private static <T> Map<String, T> reflect(String owner, Class<T> type) {
      Map<String, T> out = new LinkedHashMap<String, T>();
      try {
         Class<?> c = Class.forName(owner);
         Field[] fs = c.getDeclaredFields();
         for (int i = 0; i < fs.length; i++) {
            Field f = fs[i];
            if (!Modifier.isStatic(f.getModifiers()) || !type.isAssignableFrom(f.getType())) {
               continue;
            }

            f.setAccessible(true);
            Object v = f.get(null);
            if (v != null) {
               out.put(key(f.getName()), type.cast(v));
            }
         }
      } catch (Throwable t) {
         System.out.println("[commands] could not read " + owner + ": " + t);
      }

      return out;
   }

   private static synchronized void init() {
      if (blocks != null) {
         return;
      }

      Map<String, Block> b = reflect("net.minecraft.game.block.BlockList", Block.class);
      blocks = new LinkedHashMap<String, Integer>();
      for (Map.Entry<String, Block> e : b.entrySet()) {
         blocks.put(e.getKey(), Integer.valueOf(e.getValue().id));
      }

      Map<String, Item> it = reflect("net.minecraft.game.item.ItemList", Item.class);
      items = new LinkedHashMap<String, Integer>();
      for (Map.Entry<String, Item> e : it.entrySet()) {
         items.put(e.getKey(), Integer.valueOf(e.getValue().id));
      }

      // A block is a legal thing to hold, so block names resolve as items too -- but only
      // where an item of the same name has not already claimed the key.
      for (Map.Entry<String, Integer> e : blocks.entrySet()) {
         if (!items.containsKey(e.getKey())) {
            items.put(e.getKey(), e.getValue());
         }
      }

      effects = reflect("net.minecraft.game.item.potion.EffectList", Effect.class);
      enchants = reflect("net.minecraft.game.item.enchantment.EnchantmentList", Enchantment.class);
   }

   // ---- lookups --------------------------------------------------------------

   private static int numeric(String s) {
      try {
         return Integer.parseInt(s);
      } catch (NumberFormatException e) {
         return -1;
      }
   }

   /** Block id for a name or a raw number, or -1. */
   public static int block(String name) {
      init();
      int n = numeric(name);
      if (n >= 0) {
         return n;
      }

      Integer id = blocks.get(key(name));
      return id == null ? -1 : id.intValue();
   }

   /** Item id for a name or a raw number, or -1. Block names resolve here too. */
   public static int item(String name) {
      init();
      int n = numeric(name);
      if (n >= 0) {
         return n;
      }

      Integer id = items.get(key(name));
      return id == null ? -1 : id.intValue();
   }

   public static Effect effect(String name) {
      init();
      int n = numeric(name);
      if (n >= 0) {
         for (Effect e : effects.values()) {
            if (e.getId() == n) {
               return e;
            }
         }

         return null;
      }

      return effects.get(key(name));
   }

   public static Enchantment enchantment(String name) {
      init();
      int n = numeric(name);
      if (n >= 0) {
         for (Enchantment e : enchants.values()) {
            if (e.id == n) {
               return e;
            }
         }

         return null;
      }

      return enchants.get(key(name));
   }

   // ---- error help -----------------------------------------------------------

   private static String suggest(Map<String, ?> from, String typed) {
      String k = key(typed);
      if (k.length() == 0) {
         return "";
      }

      List<String> hits = new ArrayList<String>();
      for (String name : from.keySet()) {
         if (name.startsWith(k) || name.contains(k)) {
            hits.add(name);
         }
      }

      if (hits.isEmpty()) {
         return "";
      }

      Collections.sort(hits);
      StringBuilder b = new StringBuilder(" Did you mean: ");
      for (int i = 0; i < hits.size() && i < 5; i++) {
         if (i > 0) {
            b.append(", ");
         }

         b.append(hits.get(i));
      }

      return b.append('?').toString();
   }

   public static String suggestBlock(String typed) { init(); return suggest(blocks, typed); }
   public static String suggestItem(String typed) { init(); return suggest(items, typed); }
   public static String suggestEffect(String typed) { init(); return suggest(effects, typed); }
   public static String suggestEnchant(String typed) { init(); return suggest(enchants, typed); }
}

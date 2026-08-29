package net.minecraft.client.gui.screens.title;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiList;
import net.minecraft.client.gui.button.Button;
import net.minecraft.client.gui.screens.GuiScreen;
import net.minecraft.client.render.Tessellator;
import net.minecraft.localization.Translator;

/**
 * The mods installed for this client.
 *
 * Deliberately reads the mods folder itself rather than asking the loader. ModEngine only
 * exists in InfiniteLoader.jar, and on a profile registered into a launcher there is no
 * tweaker, so the loader never runs and there would be nothing to ask. Reading the folder
 * gives the same answer in every launch path, and still works when a jar is present but
 * disabled.
 */
public class ModsScreen extends GuiScreen {

   private GuiScreen parent;
   private GuiList list;
   private ArrayList<ModsScreen.Entry> entries = new ArrayList<ModsScreen.Entry>();
   private String subtitle = "";

   public ModsScreen(GuiScreen parent) {
      this.parent = parent;
      this.scan(new File(Minecraft.getMinecraftDir(), "mods"));
   }

   private void scan(File dir) {
      File[] files = dir.isDirectory() ? dir.listFiles() : null;
      if (files != null) {
         for (int i = 0; i < files.length; i++) {
            File f = files[i];
            String name = f.getName().toLowerCase();
            boolean disabled = name.endsWith(".jar.disabled");
            if (!f.isFile() || !name.endsWith(".jar") && !disabled) {
               continue;
            }

            String manifest = this.manifestIn(f);
            if (manifest == null) {
               // a jar with no manifest is not an addon; the loader ignores it too
               continue;
            }

            this.readMods(manifest, f, disabled);
         }
      }

      Collections.sort(this.entries, new ModsScreen.ByName());
      int active = 0;
      for (int i = 0; i < this.entries.size(); i++) {
         if (!this.entries.get(i).disabled) {
            active++;
         }
      }

      if (this.entries.isEmpty()) {
         this.subtitle = Translator.getInstance().translateKey("mods.none");
      } else {
         // spaces belong in the code, not the lang file: Properties strips leading whitespace
         // from a value, so a key defined as " installed" would come back as "installed"
         Translator t = Translator.getInstance();
         this.subtitle = active + " " + t.translateKey("mods.count");
         if (this.entries.size() > active) {
            this.subtitle = this.subtitle + ", " + (this.entries.size() - active) + " " + t.translateKey("mods.disabled");
         }
      }
   }

   /** META-INF/infinite.mods.toml out of a jar, or null when it has none. */
   private String manifestIn(File jar) {
      ZipFile zip = null;

      String var10;
      try {
         zip = new ZipFile(jar);
         ZipEntry e = zip.getEntry("META-INF/infinite.mods.toml");
         if (e == null) {
            return null;
         }

         InputStream in = zip.getInputStream(e);
         BufferedReader r = new BufferedReader(new InputStreamReader(in, Charset.forName("UTF-8")));
         StringBuilder sb = new StringBuilder();

         String line;
         while ((line = r.readLine()) != null) {
            sb.append(line).append('\n');
         }

         r.close();
         var10 = sb.toString();
      } catch (Throwable var14) {
         return null;
      } finally {
         if (zip != null) {
            try {
               zip.close();
            } catch (Throwable var13) {
            }
         }
      }

      return var10;
   }

   /**
    * Pull one entry per [[mods]] block.
    *
    * A deliberately small parser: the manifest is flat key = "value" lines, and anything it
    * cannot make sense of is skipped rather than throwing. A malformed addon should show up
    * as a missing row, never as a crash on the way to the title screen.
    */
   private void readMods(String toml, File source, boolean disabled) {
      String[] lines = toml.split("\n");
      boolean inMods = false;
      ModsScreen.Entry cur = null;

      for (int i = 0; i < lines.length; i++) {
         String line = lines[i].trim();
         if (line.length() != 0 && !line.startsWith("#")) {
            if (line.startsWith("[")) {
               if (cur != null) {
                  this.entries.add(cur);
                  cur = null;
               }

               inMods = line.startsWith("[[mods]]");
            } else if (inMods) {
               int eq = line.indexOf(61);
               if (eq > 0) {
                  String key = line.substring(0, eq).trim();
                  String val = unquote(line.substring(eq + 1).trim());
                  if (cur == null) {
                     cur = new ModsScreen.Entry(source, disabled);
                  }

                  if ("modId".equals(key)) {
                     cur.modId = val;
                  } else if ("version".equals(key)) {
                     cur.version = val;
                  } else if ("displayName".equals(key)) {
                     cur.displayName = val;
                  } else if ("description".equals(key)) {
                     cur.description = val;
                  } else if ("authors".equals(key)) {
                     cur.authors = val;
                  }
               }
            }
         }
      }

      if (cur != null) {
         this.entries.add(cur);
      }
   }

   private static String unquote(String s) {
      String t = s;
      int hash = s.indexOf(" #");
      if (hash > 0) {
         t = s.substring(0, hash).trim();
      }

      if (t.length() >= 2 && (t.startsWith("\"") && t.endsWith("\"") || t.startsWith("'") && t.endsWith("'"))) {
         return t.substring(1, t.length() - 1);
      } else {
         return t;
      }
   }

   @Override
   public void initGui() {
      this.buttons.clear();
      this.buttons.add(new Button(0, this.width / 2 - 75, this.height - 40, 150, 20, "gui.exit"));
      this.list = new ModsScreen.ModSlot(this.mc, this.width, this.height, 42, this.height - 58 + 2, 28, this);
   }

   @Override
   protected void buttonClicked(Button button) {
      if (button.id == 0) {
         this.mc.setScreen(this.parent);
      }
   }

   @Override
   public void drawScreen(int x, int y, float tickDelta) {
      this.list.draw(x, y, tickDelta);
      this.drawCenteredString(this.fontRenderer, Translator.getInstance().translateKey("menu.mods"), this.width / 2, 14, 16777215);
      this.drawCenteredString(this.fontRenderer, this.subtitle, this.width / 2, 28, 10526880);
      super.drawScreen(x, y, tickDelta);
   }

   @Override
   public void scroll(int scrollValue) {
      this.list.scrollMouse();
   }

   private static class ByName implements Comparator<ModsScreen.Entry> {
      private ByName() {
      }

      public int compare(ModsScreen.Entry a, ModsScreen.Entry b) {
         return a.name().compareToIgnoreCase(b.name());
      }
   }

   private static class Entry {
      String modId = "";
      String version = "";
      String displayName = "";
      String description = "";
      String authors = "";
      final File source;
      final boolean disabled;

      Entry(File source, boolean disabled) {
         this.source = source;
         this.disabled = disabled;
      }

      String name() {
         return this.displayName.length() > 0 ? this.displayName : this.modId;
      }

      String detail() {
         if (this.description.length() > 0) {
            return this.description;
         } else {
            return this.authors.length() > 0 ? this.authors : this.source.getName();
         }
      }
   }

   private static class ModSlot extends GuiList {
      private ModsScreen parent;

      public ModSlot(Minecraft mc, int width, int height, int top, int bottom, int slot, ModsScreen screen) {
         super(mc, width, height, top, bottom, slot);
         this.parent = screen;
      }

      protected int listSize() {
         return this.parent.entries.size();
      }

      protected void elementClicked(int click, boolean state) {
      }

      protected boolean isSelected(int state) {
         return false;
      }

      protected int contentSize() {
         return this.listSize() * this.slotHeight;
      }

      protected void drawSlot(int id, int x, int y, int offset, Tessellator tess) {
         ModsScreen.Entry e = this.parent.entries.get(id);
         String head = e.name();
         if (e.version.length() > 0) {
            head = head + " " + e.version;
         }

         if (e.disabled) {
            head = head + " " + Translator.getInstance().translateKey("mods.off");
         }

         this.drawString(this.parent.fontRenderer, head, 24, y + 2, e.disabled ? 10526880 : 16777215);
         this.drawString(this.parent.fontRenderer, e.detail(), 24, y + 14, 8421504);
      }

      protected void drawBackground() {
         this.parent.drawBackground();
      }
   }
}

package net.minecraft.client.gui.screens.options;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiList;
import net.minecraft.client.gui.button.Button;
import net.minecraft.client.gui.screens.GuiScreen;
import net.minecraft.client.input.ControllerInput;
import net.minecraft.client.options.KeyBind;
import net.minecraft.client.options.OptionsList;
import net.minecraft.client.render.Tessellator;
import net.minecraft.localization.Translator;
import org.lwjgl.input.Mouse;

/**
 * Bind gamepad buttons to the game's existing actions.
 *
 * Reached from Controls. Rebinding waits for a real button press rather than asking anyone to
 * know their pad's button numbers, and the capture happens in update() because that is the only
 * per-tick hook a screen gets.
 */
public class ControllerScreen extends GuiScreen {

   private static final int ID_DONE = 100;
   private static final int ID_ENABLED = 101;
   private static final int ID_INVERT = 102;
   private static final int ID_SPEED = 103;
   private static final int ID_DEADZONE = 104;
   private static final int ID_RESET = 105;
   private static final int ID_DETECT = 106;

   private final GuiScreen parent;
   private final OptionsList options;
   private GuiList list;
   private Button[] rows;
   private int capturing = -1;
   private int ignoreUntilReleased = -1;

   public ControllerScreen(OptionsList options, GuiScreen parent) {
      this.options = options;
      this.parent = parent;
   }

   @Override
   public void initGui() {
      this.buttons.clear();
      this.rows = new Button[ControllerInput.BINDABLE.length];
      for (int i = 0; i < ControllerInput.BINDABLE.length; i++) {
         this.rows[i] = new Button(i, 0, 0, 90, 20, "");
         this.buttons.add(this.rows[i]);
      }
      this.refreshRowLabels();

      int cx = this.width / 2;
      int bottom = this.height - 78;
      this.buttons.add(new Button(ID_ENABLED, cx - 155, bottom, 150, 20, ""));
      this.buttons.add(new Button(ID_INVERT, cx + 5, bottom, 150, 20, ""));
      this.buttons.add(new Button(ID_SPEED, cx - 155, bottom + 24, 150, 20, ""));
      this.buttons.add(new Button(ID_DEADZONE, cx + 5, bottom + 24, 150, 20, ""));
      this.buttons.add(new Button(ID_RESET, cx - 155, bottom + 48, 150, 20, "controller.reset"));
      this.buttons.add(new Button(ID_DONE, cx + 5, bottom + 48, 150, 20, "gui.done"));
      this.refreshToggleLabels();

      this.list = new ControllerScreen.BindSlot(this.mc, this.width, this.height, 44, this.height - 86, 24, this);
   }

   private void refreshRowLabels() {
      for (int i = 0; i < this.rows.length; i++) {
         this.rows[i].label = label(i);
      }
   }

   private String label(int i) {
      if (this.capturing == i) {
         return "> ? <";
      }
      int button = ControllerInput.buttonFor(ControllerInput.BINDABLE[i]);
      return button < 0
         ? Translator.getInstance().translateKey("controller.unbound")
         : Translator.getInstance().translateKey("controller.button") + " " + button;
   }

   private void refreshToggleLabels() {
      for (int i = 0; i < this.buttons.size(); i++) {
         Button b = (Button)this.buttons.get(i);
         Translator t = Translator.getInstance();
         if (b.id == ID_ENABLED) {
            b.label = t.translateKey("controller.enabled") + ": " + onOff(ControllerInput.enabled);
         } else if (b.id == ID_INVERT) {
            b.label = t.translateKey("controller.invert") + ": " + onOff(ControllerInput.invertY);
         } else if (b.id == ID_SPEED) {
            b.label = t.translateKey("controller.speed") + ": " + ControllerInput.lookSpeed;
         } else if (b.id == ID_DEADZONE) {
            b.label = t.translateKey("controller.deadzone") + ": " + ControllerInput.deadzonePct + "%";
         }
      }
   }

   private static String onOff(boolean on) {
      Translator t = Translator.getInstance();
      return on ? t.translateKey("options.on") : t.translateKey("options.off");
   }

   @Override
   protected void buttonClicked(Button button) {
      if (!button.enabled || !button.visible) {
         return;
      }

      if (button.id < this.rows.length) {
         // second click on the row that is already capturing clears the binding
         if (this.capturing == button.id) {
            ControllerInput.unbind(ControllerInput.BINDABLE[button.id]);
            this.capturing = -1;
         } else {
            this.capturing = button.id;
            this.ignoreUntilReleased = ControllerInput.pressedButton();
         }
         this.refreshRowLabels();
         return;
      }

      switch (button.id) {
         case ID_ENABLED:
            ControllerInput.enabled = !ControllerInput.enabled;
            break;
         case ID_INVERT:
            ControllerInput.invertY = !ControllerInput.invertY;
            break;
         case ID_SPEED:
            ControllerInput.lookSpeed = ControllerInput.lookSpeed >= 10 ? 1 : ControllerInput.lookSpeed + 1;
            break;
         case ID_DEADZONE:
            ControllerInput.deadzonePct = ControllerInput.deadzonePct >= 60 ? 0 : ControllerInput.deadzonePct + 5;
            break;
         case ID_RESET:
            ControllerInput.resetDefaults();
            this.refreshRowLabels();
            break;
         case ID_DETECT:
            ControllerInput.redetect();
            break;
         case ID_DONE:
            ControllerInput.save();
            this.mc.setScreen(this.parent);
            return;
         default:
            return;
      }
      ControllerInput.save();
      this.refreshToggleLabels();
   }

   /** Capture a button press while a row is armed. */
   @Override
   public void update() {
      if (this.capturing < 0) {
         return;
      }
      int pressed = ControllerInput.pressedButton();
      if (pressed < 0) {
         // the button that opened the row has been let go, so the next press is a real choice
         this.ignoreUntilReleased = -1;
         return;
      }
      if (pressed == this.ignoreUntilReleased) {
         return;
      }
      ControllerInput.bind(ControllerInput.BINDABLE[this.capturing], pressed);
      this.capturing = -1;
      this.ignoreUntilReleased = pressed;
      this.refreshRowLabels();
   }

   @Override
   protected void keyTyped(char character, int key) {
      if (this.capturing >= 0) {
         this.capturing = -1;
         this.refreshRowLabels();
         return;
      }
      if (key == 1) {
         ControllerInput.save();
         this.mc.setScreen(this.parent);
      }
   }

   @Override
   public void drawScreen(int x, int y, float tickDelta) {
      this.list.draw(x, y, tickDelta);
      Translator t = Translator.getInstance();
      this.drawCenteredString(this.fontRenderer, t.translateKey("options.controller"), this.width / 2, 14, 16777215);

      String status = ControllerInput.isAvailable()
         ? ControllerInput.deviceName()
         : t.translateKey("controller.none");
      this.drawCenteredString(this.fontRenderer, status, this.width / 2, 28,
                              ControllerInput.isAvailable() ? 10526880 : 16755200);

      if (this.capturing >= 0) {
         this.drawCenteredString(this.fontRenderer, t.translateKey("controller.press"),
                                 this.width / 2, this.height - 96, 16777045);
      }

      for (int i = this.rows.length; i < this.buttons.size(); i++) {
         ((Button)this.buttons.get(i)).drawButton(this.mc, x, y);
      }
   }

   @Override
   public void scroll(int amount) {
      this.list.scrollMouse();
   }

   public class BindSlot extends GuiList {
      private final ControllerScreen parent;

      public BindSlot(Minecraft mc, int width, int height, int top, int bottom, int slot, ControllerScreen screen) {
         super(mc, width, height, top, bottom, slot);
         this.parent = screen;
      }

      @Override
      protected int listSize() {
         return ControllerInput.BINDABLE.length;
      }

      @Override
      protected void elementClicked(int click, boolean state) {
      }

      @Override
      protected boolean isSelected(int state) {
         return false;
      }

      @Override
      protected int contentSize() {
         return this.listSize() * this.slotHeight;
      }

      @Override
      protected void drawBackground() {
         this.parent.drawBackground();
      }

      @Override
      protected void drawSlot(int id, int x, int y, int offset, Tessellator tess) {
         int sw = this.mc.rescaler.getScaledWidth();
         int sh = this.mc.rescaler.getScaledHeight();
         int mouseX = Mouse.getX() * sw / this.mc.displayWidth;
         int mouseY = sh - Mouse.getY() * sh / this.mc.displayHeight - 1;

         Button row = ControllerScreen.this.rows[id];
         row.x = this.width / 2 + 20;
         row.y = y;
         row.enabled = y >= this.top - 12 && y <= this.bottom - 14;
         row.drawButton(this.mc, mouseX, mouseY);

         KeyBind bind = ControllerInput.bindFor(ControllerInput.BINDABLE[id]);
         String name = bind == null
            ? ControllerInput.BINDABLE[id]
            : Translator.getInstance().translateKey(bind.name);
         this.drawString(this.parent.fontRenderer, name, this.width / 2 - 110, y + 6, 14737632);
      }
   }
}

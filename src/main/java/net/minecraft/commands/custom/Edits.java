package net.minecraft.commands.custom;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.game.world.World;

/**
 * The one place blocks are changed in bulk, shared by /fill, /clone and the // commands.
 *
 * Two things make bulk edits dangerous here, and both are handled once, in this class:
 *
 * Writing with notify runs neighbour physics per block, so a large region turns into hundreds
 * of thousands of block updates inside a single tick and the server stops keeping up. So the
 * bulk write is silent and the region is marked dirty once at the end, which resends the
 * chunks to everyone without running physics. That is the same trade WorldEdit makes.
 *
 * Writing into an unloaded chunk asks the generator to make one, mid-tick -- the exact cause
 * of the `Can't keep up` bug fixed in patch 0011. So every write is gated on
 * {@link World#blockExists}, and an edit reports how many blocks it skipped for that reason
 * rather than silently generating terrain.
 *
 * Every edit records what it overwrote, which is what makes undo possible. Recording is not
 * optional: an edit that could not be undone would be the one thing players cannot recover
 * from, so it is built into the write path rather than bolted onto the commands.
 */
public final class Edits {

   /** 64x64x64. Large enough to be useful, small enough that one edit cannot exhaust the heap. */
   public static final int MAX_BLOCKS = 262144;

   private static final int HISTORY = 8;

   private static final Map<String, Deque<Edit>> UNDO = new HashMap<String, Deque<Edit>>();

   private Edits() {
   }

   /** A block position packed into a long, so undo history costs 12 bytes a block, not 20. */
   private static long pack(int x, int y, int z) {
      return ((long)x & 0x3FFFFFFL) << 38 | ((long)z & 0x3FFFFFFL) << 12 | ((long)y & 0xFFFL);
   }

   private static int unpackX(long p) { return (int)(p << 0 >> 38); }
   private static int unpackZ(long p) { return (int)(p << 26 >> 38); }
   private static int unpackY(long p) { return (int)(p & 0xFFFL); }

   /** One undoable change: the blocks it overwrote, and where. */
   public static final class Edit {
      private final World world;
      private long[] pos = new long[1024];
      private int[] old = new int[1024];
      private int n;
      private int skipped;
      private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
      private int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

      Edit(World world) {
         this.world = world;
      }

      public int changed() { return this.n; }
      public int skipped() { return this.skipped; }

      private void grow() {
         if (this.n < this.pos.length) {
            return;
         }

         long[] p = new long[this.pos.length * 2];
         int[] o = new int[this.old.length * 2];
         System.arraycopy(this.pos, 0, p, 0, this.n);
         System.arraycopy(this.old, 0, o, 0, this.n);
         this.pos = p;
         this.old = o;
      }

      /**
       * Writes one block, remembering what was there. Returns false if the position was out of
       * the world or in a chunk that is not loaded -- never generates one.
       */
      public boolean set(int x, int y, int z, int id, int meta) {
         if (y < 0 || y >= this.world.getWorldHeight()) {
            return false;
         }

         if (!this.world.blockExists(x, y, z)) {
            this.skipped++;
            return false;
         }

         int wasId = this.world.getBlockId(x, y, z);
         int wasMeta = this.world.getBlockMetadata(x, y, z);
         if (wasId == id && wasMeta == meta) {
            return false;
         }

         this.grow();
         this.pos[this.n] = pack(x, y, z);
         this.old[this.n] = wasId << 4 | wasMeta & 15;
         this.n++;

         if (x < this.minX) this.minX = x;
         if (y < this.minY) this.minY = y;
         if (z < this.minZ) this.minZ = z;
         if (x > this.maxX) this.maxX = x;
         if (y > this.maxY) this.maxY = y;
         if (z > this.maxZ) this.maxZ = z;

         this.world.setBlockAndMetadata(x, y, z, id, meta);
         return true;
      }

      /** Pushes the changed region to every client, then files the edit for undo. */
      public void commit(String owner) {
         if (this.n == 0) {
            return;
         }

         this.world.markBlocksDirty(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);

         Deque<Edit> stack = UNDO.get(owner);
         if (stack == null) {
            stack = new ArrayDeque<Edit>();
            UNDO.put(owner, stack);
         }

         stack.push(this);
         while (stack.size() > HISTORY) {
            stack.removeLast();
         }
      }

      /** Puts back everything this edit overwrote. */
      int revert() {
         for (int i = this.n - 1; i >= 0; i--) {
            long p = this.pos[i];
            int x = unpackX(p), y = unpackY(p), z = unpackZ(p);
            if (this.world.blockExists(x, y, z)) {
               this.world.setBlockAndMetadata(x, y, z, this.old[i] >> 4, this.old[i] & 15);
            }
         }

         this.world.markBlocksDirty(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
         return this.n;
      }
   }

   public static Edit begin(World world) {
      return new Edit(world);
   }

   /** Undoes the caller's most recent edit. Returns blocks restored, or -1 if there was none. */
   public static int undo(String owner) {
      Deque<Edit> stack = UNDO.get(owner);
      if (stack == null || stack.isEmpty()) {
         return -1;
      }

      return stack.pop().revert();
   }

   public static int depth(String owner) {
      Deque<Edit> stack = UNDO.get(owner);
      return stack == null ? 0 : stack.size();
   }

   /** Called when a player leaves, so their history does not pin worlds in memory. */
   public static void forget(String owner) {
      UNDO.remove(owner);
   }
}

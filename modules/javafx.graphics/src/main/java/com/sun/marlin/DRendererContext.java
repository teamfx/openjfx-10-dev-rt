/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.marlin;

import com.sun.javafx.geom.Path2D;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.util.reentrant.ReentrantContext;
import com.sun.javafx.geom.Rectangle;
import com.sun.marlin.ArrayCacheConst.CacheStats;
import java.lang.ref.WeakReference;

/**
 * This class is a renderer context dedicated to a single thread
 */
public final class DRendererContext extends ReentrantContext implements MarlinConst {

    // RendererContext creation counter
    private static final AtomicInteger CTX_COUNT = new AtomicInteger(1);

    /**
     * Create a new renderer context
     *
     * @return new RendererContext instance
     */
    public static DRendererContext createContext() {
        return new DRendererContext("ctx"
                       + Integer.toString(CTX_COUNT.getAndIncrement()));
    }

    // Smallest object used as Cleaner's parent reference
    private final Object cleanerObj;
    // dirty flag indicating an exception occured during pipeline in pathTo()
    public boolean dirty = false;
    // shared data
    public final float[] float6 = new float[6];
    // shared curve (dirty) (Renderer / Stroker)
    final DCurve curve = new DCurve();
    // MarlinRenderingEngine.TransformingPathConsumer2D
    public final DTransformingPathConsumer2D transformerPC2D;
    // recycled Path2D instance (weak)
    private WeakReference<Path2D> refPath2D = null;
    // shared memory between renderer instances:
    final DRendererSharedMemory rdrMem;
    public final DRenderer renderer;
    private DRendererNoAA rendererNoAA = null;
    public final DStroker stroker;
    // Simplifies out collinear lines
    public final DCollinearSimplifier simplifier = new DCollinearSimplifier();
    public final DDasher dasher;
    // flag indicating the shape is stroked (1) or filled (0)
    int stroking = 0;

// MarlinFX specific:
    // dirty bbox rectangle
    public final Rectangle clip = new Rectangle();
    // dirty MaskMarlinAlphaConsumer
    public MaskMarlinAlphaConsumer consumer = null;

    // Array caches:
    /* clean int[] cache (zero-filled) = 4 refs */
    private final IntArrayCache cleanIntCache = new IntArrayCache(true, 4);
    /* dirty int[] cache = 4 refs */
    private final IntArrayCache dirtyIntCache = new IntArrayCache(false, 4);
    /* dirty double[] cache = 3 refs */
    private final DoubleArrayCache dirtyDoubleCache = new DoubleArrayCache(false, 3);
    /* dirty byte[] cache = 1 ref */
    private final ByteArrayCache dirtyByteCache = new ByteArrayCache(false, 1);

    // RendererContext statistics
    final RendererStats stats;

    /**
     * Constructor
     *
     * @param name context name (debugging)
     */
    DRendererContext(final String name) {
        if (LOG_CREATE_CONTEXT) {
            MarlinUtils.logInfo("new RendererContext = " + name);
        }
        this.cleanerObj = new Object();

        // create first stats (needed by newOffHeapArray):
        if (DO_STATS || DO_MONITORS) {
            stats = RendererStats.createInstance(cleanerObj, name);
            // push cache stats:
            stats.cacheStats = new CacheStats[] { cleanIntCache.stats,
                dirtyIntCache.stats, dirtyDoubleCache.stats, dirtyByteCache.stats
            };
        } else {
            stats = null;
        }

        // MarlinRenderingEngine.TransformingPathConsumer2D
        transformerPC2D = new DTransformingPathConsumer2D();

        // Renderer shared memory:
        rdrMem = new DRendererSharedMemory(this);

        // Renderer:
        renderer = new DRenderer(this);

        stroker = new DStroker(this);
        dasher = new DDasher(this);
    }

    /**
     * Disposes this renderer context:
     * clean up before reusing this context
     */
    public void dispose() {
        if (DO_STATS) {
            if (stats.totalOffHeap > stats.totalOffHeapMax) {
                stats.totalOffHeapMax = stats.totalOffHeap;
            }
            stats.totalOffHeap = 0L;
        }
        stroking = 0;
        // if context is maked as DIRTY:
        if (dirty) {
            // may happen if an exception if thrown in the pipeline processing:
            // force cleanup of all possible pipelined blocks (except Renderer):

            // Dasher:
            this.dasher.dispose();
            // Stroker:
            this.stroker.dispose();

            // mark context as CLEAN:
            dirty = false;
        }
    }

    public Path2D getPath2D() {
        // resolve reference:
        Path2D p2d = (refPath2D != null) ? refPath2D.get() : null;

        // create a new Path2D ?
        if (p2d == null) {
            p2d = new Path2D(Path2D.WIND_NON_ZERO, INITIAL_EDGES_COUNT); // 32K

            // update weak reference:
            refPath2D = new WeakReference<Path2D>(p2d);
        }
        // reset the path anyway:
        p2d.reset();
        return p2d;
    }

    public DRendererNoAA getRendererNoAA() {
        if (rendererNoAA == null) {
            rendererNoAA = new DRendererNoAA(this);
        }
        return rendererNoAA;
    }

    OffHeapArray newOffHeapArray(final long initialSize) {
        if (DO_STATS) {
            stats.totalOffHeapInitial += initialSize;
        }
        return new OffHeapArray(cleanerObj, initialSize);
    }

    IntArrayCache.Reference newCleanIntArrayRef(final int initialSize) {
        return cleanIntCache.createRef(initialSize);
    }

    IntArrayCache.Reference newDirtyIntArrayRef(final int initialSize) {
        return dirtyIntCache.createRef(initialSize);
    }

    DoubleArrayCache.Reference newDirtyDoubleArrayRef(final int initialSize) {
        return dirtyDoubleCache.createRef(initialSize);
    }

    ByteArrayCache.Reference newDirtyByteArrayRef(final int initialSize) {
        return dirtyByteCache.createRef(initialSize);
    }

    static final class DRendererSharedMemory {

        // edges [ints] stored in off-heap memory
        final OffHeapArray edges;

        // edgeBuckets ref (clean)
        final IntArrayCache.Reference edgeBuckets_ref;
        // edgeBucketCounts ref (clean)
        final IntArrayCache.Reference edgeBucketCounts_ref;

        // alphaLine ref (clean)
        final IntArrayCache.Reference alphaLine_ref;

        // crossings ref (dirty)
        final IntArrayCache.Reference crossings_ref;
        // edgePtrs ref (dirty)
        final IntArrayCache.Reference edgePtrs_ref;
        // merge sort initial arrays
        // aux_crossings ref (dirty)
        final IntArrayCache.Reference aux_crossings_ref;
        // aux_edgePtrs ref (dirty)
        final IntArrayCache.Reference aux_edgePtrs_ref;

        // blkFlags ref (clean)
        final IntArrayCache.Reference blkFlags_ref;

        DRendererSharedMemory(final DRendererContext rdrCtx) {
            edges = rdrCtx.newOffHeapArray(INITIAL_EDGES_CAPACITY); // 96K

            edgeBuckets_ref      = rdrCtx.newCleanIntArrayRef(INITIAL_BUCKET_ARRAY); // 64K
            edgeBucketCounts_ref = rdrCtx.newCleanIntArrayRef(INITIAL_BUCKET_ARRAY); // 64K

            // 2048 (pixelsize) pixel large
            alphaLine_ref = rdrCtx.newCleanIntArrayRef(INITIAL_AA_ARRAY); // 8K

            crossings_ref     = rdrCtx.newDirtyIntArrayRef(INITIAL_CROSSING_COUNT); // 2K
            aux_crossings_ref = rdrCtx.newDirtyIntArrayRef(INITIAL_CROSSING_COUNT); // 2K
            edgePtrs_ref      = rdrCtx.newDirtyIntArrayRef(INITIAL_CROSSING_COUNT); // 2K
            aux_edgePtrs_ref  = rdrCtx.newDirtyIntArrayRef(INITIAL_CROSSING_COUNT); // 2K

            blkFlags_ref = rdrCtx.newCleanIntArrayRef(INITIAL_ARRAY); // 1K = 1 tile line
        }
    }
}

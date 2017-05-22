/**
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
 */
package com.oracle.bmc.objectstorage.transfer.internal;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;

import com.oracle.bmc.objectstorage.transfer.internal.StreamChunkCreator.SubRangeInputStream;
import com.oracle.bmc.util.StreamUtils;

public class StreamChunkCreatorTest {
    private static final int CHUNK_SIZE = 5;
    private static final String[] CHUNKS =
            new String[] {"aaaaa", "bbbbb", "ccccc", "ddddd", "eeeee", "f"};
    private static final String COMPLETE_STRING = StringUtils.join(CHUNKS);
    private static final int[] RANDOM_ORDER = new int[] {3, 1, 5, 4, 0, 2};

    private InputStream stream;

    @Before
    public void setUp() {
        stream = StreamUtils.createByteArrayInputStream(COMPLETE_STRING.getBytes());
    }

    @Test
    public void serialChunks_readInOrder() throws Exception {
        StreamChunkCreator creator =
                new StreamChunkCreator(stream, COMPLETE_STRING.length(), CHUNK_SIZE);

        int chunkCount = 0;
        while (creator.hasMore()) {
            SubRangeInputStream chunk = creator.next();
            if (creator.hasMore()) {
                assertEquals(CHUNK_SIZE, chunk.length());
            } else {
                // this was the last chunk
                assertEquals(1, chunk.length());
            }
            String chunkContent = toString(chunk);
            assertEquals(CHUNKS[chunkCount], chunkContent);

            chunkCount++;
        }

        assertEquals(CHUNKS.length, chunkCount);
    }

    @Test
    public void serialChunks_readOutOfOrder() throws Exception {
        StreamChunkCreator creator =
                new StreamChunkCreator(stream, COMPLETE_STRING.length(), CHUNK_SIZE);

        ArrayList<SubRangeInputStream> chunks = new ArrayList<>();
        while (creator.hasMore()) {
            SubRangeInputStream chunk = creator.next();
            chunks.add(chunk);
        }
        assertEquals(CHUNKS.length, chunks.size());

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CHUNKS.length; i++) {
            String content = toString(chunks.get(RANDOM_ORDER[i]));
            sb.append(content);
        }
        // even reading all chunks out of order will end up reading the
        // underlying bytes in order
        assertEquals(COMPLETE_STRING, sb.toString());
    }

    @Test
    public void parallelChunks_readInOrder() throws Exception {
        StreamChunkCreator creator =
                new StreamChunkCreator(stream, COMPLETE_STRING.length(), CHUNK_SIZE);
        assertTrue(creator.enableParallelReads());

        int chunkCount = 0;
        while (creator.hasMore()) {
            SubRangeInputStream chunk = creator.next();
            if (creator.hasMore()) {
                assertEquals(CHUNK_SIZE, chunk.length());
            } else {
                // this was the last chunk
                assertEquals(1, chunk.length());
            }
            String chunkContent = toString(chunk);
            assertEquals(CHUNKS[chunkCount], chunkContent);

            chunkCount++;
        }

        assertEquals(CHUNKS.length, chunkCount);
    }

    @Test
    public void parallelChunks_readOutOfOrder() throws Exception {
        StreamChunkCreator creator =
                new StreamChunkCreator(stream, COMPLETE_STRING.length(), CHUNK_SIZE);
        assertTrue(creator.enableParallelReads());

        ArrayList<SubRangeInputStream> chunks = new ArrayList<>();
        while (creator.hasMore()) {
            SubRangeInputStream chunk = creator.next();
            chunks.add(chunk);
        }
        assertEquals(CHUNKS.length, chunks.size());

        StringBuilder sb = new StringBuilder();
        StringBuilder expected = new StringBuilder();
        for (int i = 0; i < CHUNKS.length; i++) {
            String content = toString(chunks.get(RANDOM_ORDER[i]));
            sb.append(content);
            expected.append(CHUNKS[RANDOM_ORDER[i]]);
        }
        // reading chunks out of order doesn't matter, the bytes will be from
        // the
        // correct range from the original stream
        assertEquals(expected.toString(), sb.toString());
    }

    @Test
    public void chunkSizeEqualToObjectSize() throws Exception {
        StreamChunkCreator creator =
                new StreamChunkCreator(stream, COMPLETE_STRING.length(), COMPLETE_STRING.length());
        assertTrue(creator.hasMore());
        SubRangeInputStream chunk = creator.next();
        assertEquals(COMPLETE_STRING, toString(chunk));
        assertFalse(creator.hasMore());
    }

    @Test
    public void chunkSizeGreaterThanObjectSize() throws Exception {
        StreamChunkCreator creator =
                new StreamChunkCreator(
                        stream, COMPLETE_STRING.length(), COMPLETE_STRING.length() + 1);
        assertTrue(creator.hasMore());
        SubRangeInputStream chunk = creator.next();
        assertEquals(COMPLETE_STRING, toString(chunk));
        assertFalse(creator.hasMore());
    }

    @Test
    public void subRangeInputStream_markReset() throws Exception {
        StreamChunkCreator creator =
                new StreamChunkCreator(stream, COMPLETE_STRING.length(), CHUNK_SIZE);
        assertTrue(creator.hasMore());
        SubRangeInputStream chunk = creator.next();

        assertTrue(chunk.markSupported());
        chunk.mark(Integer.MAX_VALUE);

        assertEquals(chunk.length(), chunk.available());
        String content = toString(chunk, false);
        assertEquals(CHUNKS[0], content);

        assertEquals(0, chunk.available());
        byte[] buffer = new byte[(int) chunk.length()];
        int bytesRead = chunk.read(buffer);
        assertEquals(-1, bytesRead);

        chunk.reset();
        assertEquals(chunk.length(), chunk.available());
        bytesRead = chunk.read(buffer);
        assertEquals(chunk.length(), bytesRead);
        assertEquals(CHUNKS[0], new String(buffer));
    }

    private static String toString(SubRangeInputStream chunk) throws IOException {
        return toString(chunk, true);
    }

    private static String toString(SubRangeInputStream chunk, boolean close) throws IOException {
        byte[] buffer = new byte[(int) chunk.length()];
        int bytesRead = chunk.read(buffer);
        String chunkContent = new String(buffer, 0, bytesRead);
        if (close) {
            chunk.close();
        }
        return chunkContent;
    }
}
/*
 * Copyright (C) 2017, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.internal.storage.reftable;

import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;
import static org.eclipse.jgit.lib.Ref.Storage.NEW;
import static org.eclipse.jgit.lib.Ref.Storage.PACKED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jgit.internal.storage.io.BlockSource;
import org.eclipse.jgit.internal.storage.reftable.ReftableWriter.Stats;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.Ref;
import org.junit.Test;

public class ReftableCompactorTest {
	private static final String MASTER = "refs/heads/master";
	private static final String NEXT = "refs/heads/next";

	@Test
	public void noTables() throws IOException {
		ReftableCompactor compactor;
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			compactor = new ReftableCompactor(out);
			compactor.compact();
		}
		Stats stats = compactor.getStats();
		assertEquals(0, stats.minUpdateIndex());
		assertEquals(0, stats.maxUpdateIndex());
		assertEquals(0, stats.refCount());
	}

	@Test
	public void oneTable() throws IOException {
		byte[] inTab;
		try (ByteArrayOutputStream inBuf = new ByteArrayOutputStream()) {
			ReftableWriter writer = new ReftableWriter(inBuf)
				.setMinUpdateIndex(0)
				.setMaxUpdateIndex(0)
				.begin();

			writer.writeRef(ref(MASTER, 1));
			writer.finish();
			inTab = inBuf.toByteArray();
		}

		byte[] outTab;
		ReftableCompactor compactor;
		try (ByteArrayOutputStream outBuf = new ByteArrayOutputStream()) {
			compactor = new ReftableCompactor(outBuf);
			List<ReftableReader> readers = new ArrayList<>();
			readers.add(read(inTab));
			compactor.addAll(readers);
			compactor.compact();
			outTab = outBuf.toByteArray();
		}
		Stats stats = compactor.getStats();
		assertEquals(0, stats.minUpdateIndex());
		assertEquals(0, stats.maxUpdateIndex());
		assertEquals(1, stats.refCount());

		ReftableReader rr = read(outTab);
		try (RefCursor rc = rr.allRefs()) {
			assertTrue(rc.next());
			assertEquals(MASTER, rc.getRef().getName());
			assertEquals(id(1), rc.getRef().getObjectId());
			assertEquals(0, rc.getRef().getUpdateIndex());
		}
	}

	@Test
	public void twoTablesOneRef() throws IOException {
		byte[] inTab1;
		try (ByteArrayOutputStream inBuf = new ByteArrayOutputStream()) {
			ReftableWriter writer = new ReftableWriter(inBuf)
				.setMinUpdateIndex(0)
				.setMaxUpdateIndex(0)
				.begin();

			writer.writeRef(ref(MASTER, 1));
			writer.finish();
			inTab1 = inBuf.toByteArray();
		}

		byte[] inTab2;
		try (ByteArrayOutputStream inBuf = new ByteArrayOutputStream()) {
			ReftableWriter writer = new ReftableWriter(inBuf)
				.setMinUpdateIndex(1)
				.setMaxUpdateIndex(1)
				.begin();

			writer.writeRef(ref(MASTER, 2));
			writer.finish();
			inTab2 = inBuf.toByteArray();
		}

		byte[] outTab;
		ReftableCompactor compactor;
		try (ByteArrayOutputStream outBuf = new ByteArrayOutputStream()) {
			compactor = new ReftableCompactor(outBuf);
			compactor.addAll(Arrays.asList(read(inTab1), read(inTab2)));
			compactor.compact();
			outTab = outBuf.toByteArray();
		}
		Stats stats = compactor.getStats();
		assertEquals(0, stats.minUpdateIndex());
		assertEquals(1, stats.maxUpdateIndex());
		assertEquals(1, stats.refCount());

		ReftableReader rr = read(outTab);
		try (RefCursor rc = rr.allRefs()) {
			assertTrue(rc.next());
			assertEquals(MASTER, rc.getRef().getName());
			assertEquals(id(2), rc.getRef().getObjectId());
			assertEquals(1, rc.getRef().getUpdateIndex());
		}
	}

	@Test
	public void twoTablesTwoRefs() throws IOException {
		byte[] inTab1;
		try (ByteArrayOutputStream inBuf = new ByteArrayOutputStream()) {
			ReftableWriter writer = new ReftableWriter(inBuf)
				.setMinUpdateIndex(0)
				.setMaxUpdateIndex(0)
				.begin();

			writer.writeRef(ref(MASTER, 1));
			writer.writeRef(ref(NEXT, 2));
			writer.finish();
			inTab1 = inBuf.toByteArray();
		}

		byte[] inTab2;
		try (ByteArrayOutputStream inBuf = new ByteArrayOutputStream()) {
			ReftableWriter writer = new ReftableWriter(inBuf)
				.setMinUpdateIndex(1)
				.setMaxUpdateIndex(1)
				.begin();

			writer.writeRef(ref(MASTER, 3));
			writer.finish();
			inTab2 = inBuf.toByteArray();
		}

		byte[] outTab;
		ReftableCompactor compactor;
		try (ByteArrayOutputStream outBuf = new ByteArrayOutputStream()) {
			compactor =  new ReftableCompactor(outBuf);
			compactor.addAll(Arrays.asList(read(inTab1), read(inTab2)));
			compactor.compact();
			outTab = outBuf.toByteArray();
		}
		Stats stats = compactor.getStats();
		assertEquals(0, stats.minUpdateIndex());
		assertEquals(1, stats.maxUpdateIndex());
		assertEquals(2, stats.refCount());

		ReftableReader rr = read(outTab);
		try (RefCursor rc = rr.allRefs()) {
			assertTrue(rc.next());
			assertEquals(MASTER, rc.getRef().getName());
			assertEquals(id(3), rc.getRef().getObjectId());
			assertEquals(1, rc.getRef().getUpdateIndex());

			assertTrue(rc.next());
			assertEquals(NEXT, rc.getRef().getName());
			assertEquals(id(2), rc.getRef().getObjectId());
			assertEquals(0, rc.getRef().getUpdateIndex());
		}
	}

	@Test
	public void twoTablesIncludeOneDelete() throws IOException {
		byte[] inTab1;
		try (ByteArrayOutputStream inBuf = new ByteArrayOutputStream()) {
			ReftableWriter writer = new ReftableWriter(inBuf)
				.setMinUpdateIndex(0)
				.setMaxUpdateIndex(0)
				.begin();

			writer.writeRef(ref(MASTER, 1));
			writer.finish();
			inTab1 = inBuf.toByteArray();
		}

		byte[] inTab2;
		try (ByteArrayOutputStream inBuf = new ByteArrayOutputStream()) {
			ReftableWriter writer = new ReftableWriter(inBuf)
				.setMinUpdateIndex(1)
				.setMaxUpdateIndex(1)
				.begin();

			writer.writeRef(tombstone(MASTER));
			writer.finish();
			inTab2 = inBuf.toByteArray();
		}

		byte[] outTab;
		ReftableCompactor compactor;
		try (ByteArrayOutputStream outBuf = new ByteArrayOutputStream()) {
			compactor = new ReftableCompactor(outBuf);
			compactor.setIncludeDeletes(true);
			compactor.addAll(Arrays.asList(read(inTab1), read(inTab2)));
			compactor.compact();
			outTab = outBuf.toByteArray();
		}
		Stats stats = compactor.getStats();
		assertEquals(0, stats.minUpdateIndex());
		assertEquals(1, stats.maxUpdateIndex());
		assertEquals(1, stats.refCount());

		ReftableReader rr = read(outTab);
		try (RefCursor rc = rr.allRefs()) {
			assertFalse(rc.next());
		}
	}

	@Test
	public void twoTablesNotIncludeOneDelete() throws IOException {
		byte[] inTab1;
		try (ByteArrayOutputStream inBuf = new ByteArrayOutputStream()) {
			ReftableWriter writer = new ReftableWriter(inBuf)
				.setMinUpdateIndex(0)
				.setMaxUpdateIndex(0)
				.begin();

			writer.writeRef(ref(MASTER, 1));
			writer.finish();
			inTab1 = inBuf.toByteArray();
		}

		byte[] inTab2;
		try (ByteArrayOutputStream inBuf = new ByteArrayOutputStream()) {
			ReftableWriter writer = new ReftableWriter(inBuf)
				.setMinUpdateIndex(1)
				.setMaxUpdateIndex(1)
				.begin();

			writer.writeRef(tombstone(MASTER));
			writer.finish();
			inTab2 = inBuf.toByteArray();
		}

		byte[] outTab;
		ReftableCompactor compactor;
		try (ByteArrayOutputStream outBuf = new ByteArrayOutputStream()) {
			compactor = new ReftableCompactor(outBuf);
			compactor.setIncludeDeletes(false);
			compactor.addAll(Arrays.asList(read(inTab1), read(inTab2)));
			compactor.compact();
			outTab = outBuf.toByteArray();
		}
		Stats stats = compactor.getStats();
		assertEquals(0, stats.minUpdateIndex());
		assertEquals(1, stats.maxUpdateIndex());
		assertEquals(0, stats.refCount());

		ReftableReader rr = read(outTab);
		try (RefCursor rc = rr.allRefs()) {
			assertFalse(rc.next());
		}
	}

	private static Ref ref(String name, int id) {
		return new ObjectIdRef.PeeledNonTag(PACKED, name, id(id));
	}

	private static Ref tombstone(String name) {
		return new ObjectIdRef.Unpeeled(NEW, name, null);
	}

	private static ObjectId id(int i) {
		byte[] buf = new byte[OBJECT_ID_LENGTH];
		buf[0] = (byte) (i & 0xff);
		buf[1] = (byte) ((i >>> 8) & 0xff);
		buf[2] = (byte) ((i >>> 16) & 0xff);
		buf[3] = (byte) (i >>> 24);
		return ObjectId.fromRaw(buf);
	}

	private static ReftableReader read(byte[] table) {
		return new ReftableReader(BlockSource.from(table));
	}
}

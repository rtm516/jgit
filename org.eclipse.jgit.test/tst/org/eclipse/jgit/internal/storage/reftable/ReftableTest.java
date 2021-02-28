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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jgit.lib.Constants.HEAD;
import static org.eclipse.jgit.lib.Constants.OBJECT_ID_LENGTH;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.Ref.Storage.NEW;
import static org.eclipse.jgit.lib.Ref.Storage.PACKED;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.internal.storage.io.BlockSource;
import org.eclipse.jgit.internal.storage.reftable.ReftableWriter.Stats;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectIdRef;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.SymbolicRef;
import org.hamcrest.Matchers;
import org.junit.Test;

public class ReftableTest {
	private static final byte[] LAST_UTF8_CHAR = new byte[] {
			(byte)0x10,
			(byte)0xFF,
			(byte)0xFF};

	private static final String MASTER = "refs/heads/master";
	private static final String NEXT = "refs/heads/next";
	private static final String AFTER_NEXT = "refs/heads/nextnext";
	private static final String LAST = "refs/heads/nextnextnext";
	private static final String NOT_REF_HEADS = "refs/zzz/zzz";
	private static final String V1_0 = "refs/tags/v1.0";

	private Stats stats;

	@Test
	public void emptyTable() throws IOException {
		byte[] table = write();
		assertEquals(92 /* header, footer */, table.length);
		assertEquals('R', table[0]);
		assertEquals('E', table[1]);
		assertEquals('F', table[2]);
		assertEquals('T', table[3]);
		assertEquals(0x01, table[4]);
		assertTrue(ReftableConstants.isFileHeaderMagic(table, 0, 8));
		assertTrue(ReftableConstants.isFileHeaderMagic(table, 24, 92));

		Reftable t = read(table);
		try (RefCursor rc = t.allRefs()) {
			assertFalse(rc.next());
		}
		try (RefCursor rc = t.seekRef(HEAD)) {
			assertFalse(rc.next());
		}
		try (RefCursor rc = t.seekRefsWithPrefix(R_HEADS)) {
			assertFalse(rc.next());
		}
		try (LogCursor rc = t.allLogs()) {
			assertFalse(rc.next());
		}
	}

	@Test
	public void emptyVirtualTableFromRefs() throws IOException {
		Reftable t = Reftable.from(Collections.emptyList());
		try (RefCursor rc = t.allRefs()) {
			assertFalse(rc.next());
		}
		try (RefCursor rc = t.seekRef(HEAD)) {
			assertFalse(rc.next());
		}
		try (LogCursor rc = t.allLogs()) {
			assertFalse(rc.next());
		}
	}

	@Test
	public void estimateCurrentBytesOneRef() throws IOException {
		Ref exp = ref(MASTER, 1);
		int expBytes = 24 + 4 + 5 + 4 + MASTER.length() + 20 + 68;

		byte[] table;
		ReftableConfig cfg = new ReftableConfig();
		cfg.setIndexObjects(false);
		try (ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
			ReftableWriter writer = new ReftableWriter(buf).setConfig(cfg);
			writer.begin();
			assertEquals(92, writer.estimateTotalBytes());
			writer.writeRef(exp);
			assertEquals(expBytes, writer.estimateTotalBytes());
			writer.finish();
			table = buf.toByteArray();
		}
		assertEquals(expBytes, table.length);
	}

	@Test
	public void estimateCurrentBytesWithIndex() throws IOException {
		List<Ref> refs = new ArrayList<>();
		for (int i = 1; i <= 5670; i++) {
			@SuppressWarnings("boxing")
			Ref ref = ref(String.format("refs/heads/%04d", i), i);
			refs.add(ref);
		}

		ReftableConfig cfg = new ReftableConfig();
		cfg.setIndexObjects(false);
		cfg.setMaxIndexLevels(1);

		int expBytes = 147860;
		byte[] table;
		try (ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
			ReftableWriter writer = new ReftableWriter(buf).setConfig(cfg);
			writer.begin();
			writer.sortAndWriteRefs(refs);
			assertEquals(expBytes, writer.estimateTotalBytes());
			writer.finish();
			stats = writer.getStats();
			table = buf.toByteArray();
		}
		assertEquals(1, stats.refIndexLevels());
		assertEquals(expBytes, table.length);
	}

	@Test
	public void hasObjMapRefs() throws IOException {
		ArrayList<Ref> refs = new ArrayList<>();
		refs.add(ref(MASTER, 1));
		byte[] table = write(refs);
		ReftableReader t = read(table);
		assertTrue(t.hasObjectMap());
	}

	@Test
	public void hasObjMapRefsSmallTable() throws IOException {
		ArrayList<Ref> refs = new ArrayList<>();
		ReftableConfig cfg = new ReftableConfig();
		cfg.setIndexObjects(false);
		refs.add(ref(MASTER, 1));
		byte[] table = write(refs);
		ReftableReader t = read(table);
		assertTrue(t.hasObjectMap());
	}

	@Test
	public void hasObjLogs() throws IOException {
		PersonIdent who = new PersonIdent("Log", "Ger", 1500079709, -8 * 60);
		String msg = "test";
		ReftableConfig cfg = new ReftableConfig();
		cfg.setIndexObjects(false);

		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		ReftableWriter writer = new ReftableWriter(buffer)
			.setMinUpdateIndex(1)
			.setConfig(cfg)
			.setMaxUpdateIndex(1)
			.begin();

		writer.writeLog("master", 1, who, ObjectId.zeroId(), id(1), msg);
		writer.finish();
		byte[] table = buffer.toByteArray();

		ReftableReader t = read(table);
		assertTrue(t.hasObjectMap());
	}

	@Test
	public void hasObjMapRefsNoIndexObjects() throws IOException {
		ArrayList<Ref> refs = new ArrayList<>();
		ReftableConfig cfg = new ReftableConfig();
		cfg.setIndexObjects(false);
		cfg.setRefBlockSize(256);
		cfg.setAlignBlocks(true);

		// Fill up 5 blocks.
		int N = 256 * 5 / 25;
		for (int i= 0; i < N; i++) {
			@SuppressWarnings("boxing")
			Ref ref = ref(String.format("%02d/xxxxxxxxxx", i), i);
			refs.add(ref);
		}
		byte[] table = write(refs, cfg);

		ReftableReader t = read(table);
		assertFalse(t.hasObjectMap());
	}

	@Test
	public void oneIdRef() throws IOException {
		Ref exp = ref(MASTER, 1);
		byte[] table = write(exp);
		assertEquals(24 + 4 + 5 + 4 + MASTER.length() + 20 + 68, table.length);

		ReftableReader t = read(table);
		try (RefCursor rc = t.allRefs()) {
			assertTrue(rc.next());
			Ref act = rc.getRef();
			assertNotNull(act);
			assertEquals(PACKED, act.getStorage());
			assertTrue(act.isPeeled());
			assertFalse(act.isSymbolic());
			assertEquals(exp.getName(), act.getName());
			assertEquals(exp.getObjectId(), act.getObjectId());
			assertEquals(0, act.getUpdateIndex());
			assertNull(act.getPeeledObjectId());
			assertFalse(rc.wasDeleted());
			assertFalse(rc.next());
		}
		try (RefCursor rc = t.seekRef(MASTER)) {
			assertTrue(rc.next());
			Ref act = rc.getRef();
			assertNotNull(act);
			assertEquals(exp.getName(), act.getName());
			assertEquals(0, act.getUpdateIndex());
			assertFalse(rc.next());
		}
	}

	@Test
	public void oneTagRef() throws IOException {
		Ref exp = tag(V1_0, 1, 2);
		byte[] table = write(exp);
		assertEquals(24 + 4 + 5 + 3 + V1_0.length() + 40 + 68, table.length);

		ReftableReader t = read(table);
		try (RefCursor rc = t.allRefs()) {
			assertTrue(rc.next());
			Ref act = rc.getRef();
			assertNotNull(act);
			assertEquals(PACKED, act.getStorage());
			assertTrue(act.isPeeled());
			assertFalse(act.isSymbolic());
			assertEquals(exp.getName(), act.getName());
			assertEquals(exp.getObjectId(), act.getObjectId());
			assertEquals(exp.getPeeledObjectId(), act.getPeeledObjectId());
			assertEquals(0, act.getUpdateIndex());
		}
	}

	@Test
	public void oneSymbolicRef() throws IOException {
		Ref exp = sym(HEAD, MASTER);
		byte[] table = write(exp);
		assertEquals(
				24 + 4 + 5 + 2 + HEAD.length() + 2 + MASTER.length() + 68,
				table.length);

		ReftableReader t = read(table);
		try (RefCursor rc = t.allRefs()) {
			assertTrue(rc.next());
			Ref act = rc.getRef();
			assertNotNull(act);
			assertTrue(act.isSymbolic());
			assertEquals(exp.getName(), act.getName());
			assertNotNull(act.getLeaf());
			assertEquals(MASTER, act.getTarget().getName());
			assertNull(act.getObjectId());
			assertEquals(0, act.getUpdateIndex());
		}
	}

	@Test
	public void resolveSymbolicRef() throws IOException {
		Reftable t = read(write(
				sym(HEAD, "refs/heads/tmp"),
				sym("refs/heads/tmp", MASTER),
				ref(MASTER, 1)));

		Ref head = t.exactRef(HEAD);
		assertNull(head.getObjectId());
		assertEquals("refs/heads/tmp", head.getTarget().getName());
		assertEquals(0, head.getUpdateIndex());

		head = t.resolve(head);
		assertNotNull(head);
		assertEquals(id(1), head.getObjectId());
		assertEquals(0, head.getUpdateIndex());

		Ref master = t.exactRef(MASTER);
		assertNotNull(master);
		assertSame(master, t.resolve(master));
		assertEquals(0, master.getUpdateIndex());
	}

	@Test
	public void failDeepChainOfSymbolicRef() throws IOException {
		Reftable t = read(write(
				sym(HEAD, "refs/heads/1"),
				sym("refs/heads/1", "refs/heads/2"),
				sym("refs/heads/2", "refs/heads/3"),
				sym("refs/heads/3", "refs/heads/4"),
				sym("refs/heads/4", "refs/heads/5"),
				sym("refs/heads/5", MASTER),
				ref(MASTER, 1)));

		Ref head = t.exactRef(HEAD);
		assertNull(head.getObjectId());
		assertNull(t.resolve(head));
	}

	@Test
	public void oneDeletedRef() throws IOException {
		String name = "refs/heads/gone";
		Ref exp = newRef(name);
		byte[] table = write(exp);
		assertEquals(24 + 4 + 5 + 3 + name.length() + 68, table.length);

		ReftableReader t = read(table);
		try (RefCursor rc = t.allRefs()) {
			assertFalse(rc.next());
		}

		t.setIncludeDeletes(true);
		try (RefCursor rc = t.allRefs()) {
			assertTrue(rc.next());
			Ref act = rc.getRef();
			assertNotNull(act);
			assertFalse(act.isSymbolic());
			assertEquals(name, act.getName());
			assertEquals(NEW, act.getStorage());
			assertNull(act.getObjectId());
			assertTrue(rc.wasDeleted());
		}
	}

	@Test
	public void seekNotFound() throws IOException {
		Ref exp = ref(MASTER, 1);
		ReftableReader t = read(write(exp));
		try (RefCursor rc = t.seekRef("refs/heads/a")) {
			assertFalse(rc.next());
		}
		try (RefCursor rc = t.seekRef("refs/heads/n")) {
			assertFalse(rc.next());
		}
	}

	@Test
	public void namespaceNotFound() throws IOException {
		Ref exp = ref(MASTER, 1);
		ReftableReader t = read(write(exp));
		try (RefCursor rc = t.seekRefsWithPrefix("refs/changes/")) {
			assertFalse(rc.next());
		}
		try (RefCursor rc = t.seekRefsWithPrefix("refs/tags/")) {
			assertFalse(rc.next());
		}
	}

	@Test
	public void namespaceHeads() throws IOException {
		Ref master = ref(MASTER, 1);
		Ref next = ref(NEXT, 2);
		Ref v1 = tag(V1_0, 3, 4);

		ReftableReader t = read(write(master, next, v1));
		try (RefCursor rc = t.seekRefsWithPrefix("refs/tags/")) {
			assertTrue(rc.next());
			assertEquals(V1_0, rc.getRef().getName());
			assertEquals(0, rc.getRef().getUpdateIndex());
			assertFalse(rc.next());
		}
		try (RefCursor rc = t.seekRefsWithPrefix("refs/heads/")) {
			assertTrue(rc.next());
			assertEquals(MASTER, rc.getRef().getName());
			assertEquals(0, rc.getRef().getUpdateIndex());

			assertTrue(rc.next());
			assertEquals(NEXT, rc.getRef().getName());
			assertEquals(0, rc.getRef().getUpdateIndex());

			assertFalse(rc.next());
		}
	}

	@Test
	public void seekPastRefWithRefCursor() throws IOException {
		Ref exp = ref(MASTER, 1);
		Ref next = ref(NEXT, 2);
		Ref afterNext = ref(AFTER_NEXT, 3);
		Ref afterNextNext = ref(LAST, 4);
		ReftableReader t = read(write(exp, next, afterNext, afterNextNext));
		try (RefCursor rc = t.seekRefsWithPrefix("")) {
			assertTrue(rc.next());
			assertEquals(MASTER, rc.getRef().getName());

			rc.seekPastPrefix("refs/heads/next/");

			assertTrue(rc.next());
			assertEquals(AFTER_NEXT, rc.getRef().getName());
			assertTrue(rc.next());
			assertEquals(LAST, rc.getRef().getName());

			assertFalse(rc.next());
		}
	}

	@Test
	public void seekPastToNonExistentPrefixToTheMiddle() throws IOException {
		Ref exp = ref(MASTER, 1);
		Ref next = ref(NEXT, 2);
		Ref afterNext = ref(AFTER_NEXT, 3);
		Ref afterNextNext = ref(LAST, 4);
		ReftableReader t = read(write(exp, next, afterNext, afterNextNext));
		try (RefCursor rc = t.seekRefsWithPrefix("")) {
			rc.seekPastPrefix("refs/heads/master_non_existent");

			assertTrue(rc.next());
			assertEquals(NEXT, rc.getRef().getName());

			assertTrue(rc.next());
			assertEquals(AFTER_NEXT, rc.getRef().getName());

			assertTrue(rc.next());
			assertEquals(LAST, rc.getRef().getName());

			assertFalse(rc.next());
		}
	}

	@Test
	public void seekPastToNonExistentPrefixToTheEnd() throws IOException {
		Ref exp = ref(MASTER, 1);
		Ref next = ref(NEXT, 2);
		Ref afterNext = ref(AFTER_NEXT, 3);
		Ref afterNextNext = ref(LAST, 4);
		ReftableReader t = read(write(exp, next, afterNext, afterNextNext));
		try (RefCursor rc = t.seekRefsWithPrefix("")) {
			rc.seekPastPrefix("refs/heads/nextnon_existent_end");
			assertFalse(rc.next());
		}
	}

	@Test
	public void seekPastWithSeekRefsWithPrefix() throws IOException {
		Ref exp = ref(MASTER, 1);
		Ref next = ref(NEXT, 2);
		Ref afterNext = ref(AFTER_NEXT, 3);
		Ref afterNextNext = ref(LAST, 4);
		Ref notRefsHeads = ref(NOT_REF_HEADS, 5);
		ReftableReader t = read(write(exp, next, afterNext, afterNextNext, notRefsHeads));
		try (RefCursor rc = t.seekRefsWithPrefix("refs/heads/")) {
			rc.seekPastPrefix("refs/heads/next/");
			assertTrue(rc.next());
			assertEquals(AFTER_NEXT, rc.getRef().getName());
			assertTrue(rc.next());
			assertEquals(LAST, rc.getRef().getName());

			// NOT_REF_HEADS is next, but it's omitted because of
			// seekRefsWithPrefix("refs/heads/").
			assertFalse(rc.next());
		}
	}

	@Test
	public void seekPastWithLotsOfRefs() throws IOException {
		Ref[] refs = new Ref[500];
		for (int i = 1; i <= 500; i++) {
			refs[i - 1] = ref(String.format("refs/%d", Integer.valueOf(i)), i);
		}
		ReftableReader t = read(write(refs));
		try (RefCursor rc = t.allRefs()) {
			rc.seekPastPrefix("refs/3");
			assertTrue(rc.next());
			assertEquals("refs/4", rc.getRef().getName());
			assertTrue(rc.next());
			assertEquals("refs/40", rc.getRef().getName());

			rc.seekPastPrefix("refs/8");
			assertTrue(rc.next());
			assertEquals("refs/9", rc.getRef().getName());
			assertTrue(rc.next());
			assertEquals("refs/90", rc.getRef().getName());
			assertTrue(rc.next());
			assertEquals("refs/91", rc.getRef().getName());
		}
	}

	@Test
	public void seekPastManyTimes() throws IOException {
		Ref exp = ref(MASTER, 1);
		Ref next = ref(NEXT, 2);
		Ref afterNext = ref(AFTER_NEXT, 3);
		Ref afterNextNext = ref(LAST, 4);
		ReftableReader t = read(write(exp, next, afterNext, afterNextNext));

		try (RefCursor rc = t.seekRefsWithPrefix("")) {
			rc.seekPastPrefix("refs/heads/master");
			rc.seekPastPrefix("refs/heads/next");
			rc.seekPastPrefix("refs/heads/nextnext");
			rc.seekPastPrefix("refs/heads/nextnextnext");
			assertFalse(rc.next());
		}
	}

	@Test
	public void seekPastOnEmptyTable() throws IOException {
		ReftableReader t = read(write());
		try (RefCursor rc = t.seekRefsWithPrefix("")) {
			rc.seekPastPrefix("refs/");
			assertFalse(rc.next());
		}
	}

	@Test
	public void indexScan() throws IOException {
		List<Ref> refs = new ArrayList<>();
		for (int i = 1; i <= 5670; i++) {
			@SuppressWarnings("boxing")
			Ref ref = ref(String.format("refs/heads/%04d", i), i);
			refs.add(ref);
		}

		byte[] table = write(refs);
		assertTrue(stats.refIndexLevels() > 0);
		assertTrue(stats.refIndexSize() > 0);
		assertScan(refs, read(table));
	}

	@Test
	public void indexSeek() throws IOException {
		List<Ref> refs = new ArrayList<>();
		for (int i = 1; i <= 5670; i++) {
			@SuppressWarnings("boxing")
			Ref ref = ref(String.format("refs/heads/%04d", i), i);
			refs.add(ref);
		}

		byte[] table = write(refs);
		assertTrue(stats.refIndexLevels() > 0);
		assertTrue(stats.refIndexSize() > 0);
		assertSeek(refs, read(table));
	}

	@Test
	public void noIndexScan() throws IOException {
		List<Ref> refs = new ArrayList<>();
		for (int i = 1; i <= 567; i++) {
			@SuppressWarnings("boxing")
			Ref ref = ref(String.format("refs/heads/%03d", i), i);
			refs.add(ref);
		}

		byte[] table = write(refs);
		assertEquals(0, stats.refIndexLevels());
		assertEquals(0, stats.refIndexSize());
		assertEquals(table.length, stats.totalBytes());
		assertScan(refs, read(table));
	}

	@Test
	public void noIndexSeek() throws IOException {
		List<Ref> refs = new ArrayList<>();
		for (int i = 1; i <= 567; i++) {
			@SuppressWarnings("boxing")
			Ref ref = ref(String.format("refs/heads/%03d", i), i);
			refs.add(ref);
		}

		byte[] table = write(refs);
		assertEquals(0, stats.refIndexLevels());
		assertSeek(refs, read(table));
	}

	@Test
	public void invalidRefWriteOrderSortAndWrite() {
		Ref master = ref(MASTER, 1);
		ReftableWriter writer = new ReftableWriter(new ByteArrayOutputStream())
			.setMinUpdateIndex(1)
			.setMaxUpdateIndex(1)
			.begin();

		List<Ref> refs = new ArrayList<>();
		refs.add(master);
		refs.add(master);

		IllegalArgumentException e  = assertThrows(
			IllegalArgumentException.class,
			() -> writer.sortAndWriteRefs(refs));
		assertThat(e.getMessage(), containsString("records must be increasing"));
	}

	@Test
	public void invalidReflogWriteOrderUpdateIndex() throws IOException {
		ReftableWriter writer = new ReftableWriter(new ByteArrayOutputStream())
			.setMinUpdateIndex(1)
			.setMaxUpdateIndex(2)
			.begin();
		PersonIdent who = new PersonIdent("Log", "Ger", 1500079709, -8 * 60);
		String msg = "test";

		writer.writeLog(MASTER, 1, who, ObjectId.zeroId(), id(1), msg);
		IllegalArgumentException e  = assertThrows(IllegalArgumentException.class,
			() -> writer.writeLog(
				MASTER, 2, who, ObjectId.zeroId(), id(2), msg));
		assertThat(e.getMessage(), containsString("records must be increasing"));
	}

	@Test
	public void invalidReflogWriteOrderName() throws IOException {
		ReftableWriter writer = new ReftableWriter(new ByteArrayOutputStream())
			.setMinUpdateIndex(1)
			.setMaxUpdateIndex(1)
			.begin();
		PersonIdent who = new PersonIdent("Log", "Ger", 1500079709, -8 * 60);
		String msg = "test";

		writer.writeLog(NEXT, 1, who, ObjectId.zeroId(), id(1), msg);
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
			() -> writer.writeLog(
				MASTER, 1, who, ObjectId.zeroId(), id(2), msg));
		assertThat(e.getMessage(), containsString("records must be increasing"));
	}

	@Test
	public void withReflog() throws IOException {
		Ref master = ref(MASTER, 1);
		Ref next = ref(NEXT, 2);
		PersonIdent who = new PersonIdent("Log", "Ger", 1500079709, -8 * 60);
		String msg = "test";

		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		ReftableWriter writer = new ReftableWriter(buffer)
				.setMinUpdateIndex(1)
				.setMaxUpdateIndex(1)
				.begin();

		writer.writeRef(master);
		writer.writeRef(next);

		writer.writeLog(MASTER, 1, who, ObjectId.zeroId(), id(1), msg);
		writer.writeLog(NEXT, 1, who, ObjectId.zeroId(), id(2), msg);

		writer.finish();
		byte[] table = buffer.toByteArray();
		assertEquals(247, table.length);

		ReftableReader t = read(table);
		try (RefCursor rc = t.allRefs()) {
			assertTrue(rc.next());
			assertEquals(MASTER, rc.getRef().getName());
			assertEquals(id(1), rc.getRef().getObjectId());
			assertEquals(1, rc.getRef().getUpdateIndex());

			assertTrue(rc.next());
			assertEquals(NEXT, rc.getRef().getName());
			assertEquals(id(2), rc.getRef().getObjectId());
			assertEquals(1, rc.getRef().getUpdateIndex());
			assertFalse(rc.next());
		}
		try (LogCursor lc = t.allLogs()) {
			assertTrue(lc.next());
			assertEquals(MASTER, lc.getRefName());
			assertEquals(1, lc.getUpdateIndex());
			assertEquals(ObjectId.zeroId(), lc.getReflogEntry().getOldId());
			assertEquals(id(1), lc.getReflogEntry().getNewId());
			assertEquals(who, lc.getReflogEntry().getWho());
			assertEquals(msg, lc.getReflogEntry().getComment());

			assertTrue(lc.next());
			assertEquals(NEXT, lc.getRefName());
			assertEquals(1, lc.getUpdateIndex());
			assertEquals(ObjectId.zeroId(), lc.getReflogEntry().getOldId());
			assertEquals(id(2), lc.getReflogEntry().getNewId());
			assertEquals(who, lc.getReflogEntry().getWho());
			assertEquals(msg, lc.getReflogEntry().getComment());

			assertFalse(lc.next());
		}
	}

	@Test
	public void reflogReader() throws IOException {
		Ref master = ref(MASTER, 1);
		Ref next = ref(NEXT, 2);

		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		ReftableWriter writer = new ReftableWriter(buffer).setMinUpdateIndex(1)
				.setMaxUpdateIndex(1).begin();

		writer.writeRef(master);
		writer.writeRef(next);

		PersonIdent who1 = new PersonIdent("Log", "Ger", 1500079709, -8 * 60);
		writer.writeLog(MASTER, 3, who1, ObjectId.zeroId(), id(1), "1");
		PersonIdent who2 = new PersonIdent("Log", "Ger", 1500079710, -8 * 60);
		writer.writeLog(MASTER, 2, who2, id(1), id(2), "2");
		PersonIdent who3 = new PersonIdent("Log", "Ger", 1500079711, -8 * 60);
		writer.writeLog(MASTER, 1, who3, id(2), id(3), "3");

		writer.finish();
		byte[] table = buffer.toByteArray();

		ReentrantLock lock = new ReentrantLock();
		ReftableReader t = read(table);
		ReftableReflogReader rlr = new ReftableReflogReader(lock, t, MASTER);

		assertEquals(rlr.getLastEntry().getWho(), who1);
		List<PersonIdent> all = rlr.getReverseEntries().stream()
				.map(x -> x.getWho()).collect(Collectors.toList());
		Matchers.contains(all, who3, who2, who1);

		assertEquals(rlr.getReverseEntry(1).getWho(), who2);

		List<ReflogEntry> reverse2 = rlr.getReverseEntries(2);
		Matchers.contains(reverse2, who3, who2);

		List<PersonIdent> more = rlr.getReverseEntries(4).stream()
				.map(x -> x.getWho()).collect(Collectors.toList());
		assertEquals(all, more);
	}

	@Test
	public void allRefs() throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		ReftableConfig cfg = new ReftableConfig();
		cfg.setRefBlockSize(1024);
		cfg.setLogBlockSize(1024);
		cfg.setAlignBlocks(true);
		ReftableWriter writer = new ReftableWriter(buffer)
				.setMinUpdateIndex(1)
				.setMaxUpdateIndex(1)
				.setConfig(cfg)
				.begin();
		PersonIdent who = new PersonIdent("Log", "Ger", 1500079709, -8 * 60);

		// Fill out the 1st ref block.
		List<String> names = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			@SuppressWarnings("boxing")
			String name = new String(new char[220]).replace("\0", String.format("%c", i + 'a'));
			names.add(name);
			writer.writeRef(ref(name, i));
		}

		// Add some log data.
		writer.writeLog(MASTER, 1, who, ObjectId.zeroId(), id(1), "msg");
		writer.finish();
		byte[] table = buffer.toByteArray();

		ReftableReader t = read(table);
		RefCursor c = t.allRefs();

		int j = 0;
		while (c.next()) {
			assertEquals(names.get(j), c.getRef().getName());
			j++;
		}
	}


	@Test
	public void reflogSeek() throws IOException {
		PersonIdent who = new PersonIdent("Log", "Ger", 1500079709, -8 * 60);
		String msg = "test";
		String msgNext = "test next";

		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		ReftableWriter writer = new ReftableWriter(buffer)
				.setMinUpdateIndex(1)
				.setMaxUpdateIndex(1)
				.begin();

		writer.writeLog(MASTER, 1, who, ObjectId.zeroId(), id(1), msg);
		writer.writeLog(NEXT, 1, who, ObjectId.zeroId(), id(2), msgNext);

		writer.finish();
		byte[] table = buffer.toByteArray();

		ReftableReader t = read(table);
		try (LogCursor c = t.seekLog(MASTER, Long.MAX_VALUE)) {
			assertTrue(c.next());
			assertEquals(c.getReflogEntry().getComment(), msg);
		}
		try (LogCursor c = t.seekLog(MASTER, 0)) {
			assertFalse(c.next());
		}
		try (LogCursor c = t.seekLog(MASTER, 1)) {
			assertTrue(c.next());
			assertEquals(c.getUpdateIndex(), 1);
			assertEquals(c.getReflogEntry().getComment(), msg);
		}
		try (LogCursor c = t.seekLog(NEXT, Long.MAX_VALUE)) {
			assertTrue(c.next());
			assertEquals(c.getReflogEntry().getComment(), msgNext);
		}
		try (LogCursor c = t.seekLog(NEXT, 0)) {
			assertFalse(c.next());
		}
		try (LogCursor c = t.seekLog(NEXT, 1)) {
			assertTrue(c.next());
			assertEquals(c.getUpdateIndex(), 1);
			assertEquals(c.getReflogEntry().getComment(), msgNext);
		}
	}

	@Test
	public void reflogSeekPrefix() throws IOException {
		PersonIdent who = new PersonIdent("Log", "Ger", 1500079709, -8 * 60);

		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		ReftableWriter writer = new ReftableWriter(buffer)
			.setMinUpdateIndex(1)
			.setMaxUpdateIndex(1)
			.begin();

		writer.writeLog("branchname", 1, who, ObjectId.zeroId(), id(1), "branchname");

		writer.finish();
		byte[] table = buffer.toByteArray();

		ReftableReader t = read(table);
		try (LogCursor c = t.seekLog("branch", Long.MAX_VALUE)) {
			// We find a reflog block, but the iteration won't confuse branchname
			// and branch.
			assertFalse(c.next());
		}
	}

	@Test
	public void onlyReflog() throws IOException {
		PersonIdent who = new PersonIdent("Log", "Ger", 1500079709, -8 * 60);
		String msg = "test";

		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		ReftableWriter writer = new ReftableWriter(buffer)
				.setMinUpdateIndex(1)
				.setMaxUpdateIndex(1)
				.begin();
		writer.writeLog(MASTER, 1, who, ObjectId.zeroId(), id(1), msg);
		writer.writeLog(NEXT, 1, who, ObjectId.zeroId(), id(2), msg);
		writer.finish();
		byte[] table = buffer.toByteArray();
		stats = writer.getStats();
		assertEquals(170, table.length);
		assertEquals(0, stats.refCount());
		assertEquals(0, stats.refBytes());
		assertEquals(0, stats.refIndexLevels());

		ReftableReader t = read(table);
		try (RefCursor rc = t.allRefs()) {
			assertFalse(rc.next());
		}
		try (RefCursor rc = t.seekRefsWithPrefix("refs/heads/")) {
			assertFalse(rc.next());
		}
		try (LogCursor lc = t.allLogs()) {
			assertTrue(lc.next());
			assertEquals(MASTER, lc.getRefName());
			assertEquals(1, lc.getUpdateIndex());
			assertEquals(ObjectId.zeroId(), lc.getReflogEntry().getOldId());
			assertEquals(id(1), lc.getReflogEntry().getNewId());
			assertEquals(who, lc.getReflogEntry().getWho());
			// compare string too, to catch tz differences.
			assertEquals(who.toExternalString(), lc.getReflogEntry().getWho().toExternalString());
			assertEquals(msg, lc.getReflogEntry().getComment());

			assertTrue(lc.next());
			assertEquals(NEXT, lc.getRefName());
			assertEquals(1, lc.getUpdateIndex());
			assertEquals(ObjectId.zeroId(), lc.getReflogEntry().getOldId());
			assertEquals(id(2), lc.getReflogEntry().getNewId());
			assertEquals(who, lc.getReflogEntry().getWho());
			assertEquals(msg, lc.getReflogEntry().getComment());

			assertFalse(lc.next());
		}
	}

	@Test
	public void logScan() throws IOException {
		ReftableConfig cfg = new ReftableConfig();
		cfg.setRefBlockSize(256);
		cfg.setLogBlockSize(2048);

		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		ReftableWriter writer = new ReftableWriter(cfg, buffer);
		writer.setMinUpdateIndex(1).setMaxUpdateIndex(1).begin();

		List<Ref> refs = new ArrayList<>();
		for (int i = 1; i <= 5670; i++) {
			@SuppressWarnings("boxing")
			Ref ref = ref(String.format("refs/heads/%04d", i), i);
			refs.add(ref);
			writer.writeRef(ref);
		}

		PersonIdent who = new PersonIdent("Log", "Ger", 1500079709, -8 * 60);
		for (Ref ref : refs) {
			writer.writeLog(ref.getName(), 1, who,
					ObjectId.zeroId(), ref.getObjectId(),
					"create " + ref.getName());
		}
		writer.finish();
		stats = writer.getStats();
		assertTrue(stats.logBytes() > 4096);
		byte[] table = buffer.toByteArray();

		ReftableReader t = read(table);
		try (LogCursor lc = t.allLogs()) {
			for (Ref exp : refs) {
				assertTrue("has " + exp.getName(), lc.next());
				assertEquals(exp.getName(), lc.getRefName());
				ReflogEntry entry = lc.getReflogEntry();
				assertNotNull(entry);
				assertEquals(who, entry.getWho());
				assertEquals(ObjectId.zeroId(), entry.getOldId());
				assertEquals(exp.getObjectId(), entry.getNewId());
				assertEquals("create " + exp.getName(), entry.getComment());
			}
			assertFalse(lc.next());
		}
	}

	@Test
	public void byObjectIdOneRefNoIndex() throws IOException {
		List<Ref> refs = new ArrayList<>();
		for (int i = 1; i <= 200; i++) {
			@SuppressWarnings("boxing")
			Ref ref = ref(String.format("refs/heads/%02d", i), i);
			refs.add(ref);
		}
		refs.add(ref("refs/heads/master", 100));

		ReftableReader t = read(write(refs));
		assertEquals(0, stats.objIndexSize());

		try (RefCursor rc = t.byObjectId(id(42))) {
			assertTrue("has 42", rc.next());
			assertEquals("refs/heads/42", rc.getRef().getName());
			assertEquals(id(42), rc.getRef().getObjectId());
			assertEquals(0, rc.getRef().getUpdateIndex());
			assertFalse(rc.next());
		}
		try (RefCursor rc = t.byObjectId(id(100))) {
			assertTrue("has 100", rc.next());
			assertEquals("refs/heads/100", rc.getRef().getName());
			assertEquals(id(100), rc.getRef().getObjectId());

			assertTrue("has master", rc.next());
			assertEquals("refs/heads/master", rc.getRef().getName());
			assertEquals(id(100), rc.getRef().getObjectId());
			assertEquals(0, rc.getRef().getUpdateIndex());

			assertFalse(rc.next());
		}
	}

	@Test
	public void byObjectIdOneRefWithIndex() throws IOException {
		List<Ref> refs = new ArrayList<>();
		for (int i = 1; i <= 5200; i++) {
			@SuppressWarnings("boxing")
			Ref ref = ref(String.format("refs/heads/%02d", i), i);
			refs.add(ref);
		}
		refs.add(ref("refs/heads/master", 100));

		ReftableReader t = read(write(refs));
		assertTrue(stats.objIndexSize() > 0);

		try (RefCursor rc = t.byObjectId(id(42))) {
			assertTrue("has 42", rc.next());
			assertEquals("refs/heads/42", rc.getRef().getName());
			assertEquals(id(42), rc.getRef().getObjectId());
			assertEquals(0, rc.getRef().getUpdateIndex());
			assertFalse(rc.next());
		}
		try (RefCursor rc = t.byObjectId(id(100))) {
			assertTrue("has 100", rc.next());
			assertEquals("refs/heads/100", rc.getRef().getName());
			assertEquals(id(100), rc.getRef().getObjectId());

			assertTrue("has master", rc.next());
			assertEquals("refs/heads/master", rc.getRef().getName());
			assertEquals(id(100), rc.getRef().getObjectId());
			assertEquals(0, rc.getRef().getUpdateIndex());

			assertFalse(rc.next());
		}
	}

	@Test
	public void byObjectIdSkipPastPrefix() throws IOException {
		ReftableReader t = read(write());
		try (RefCursor rc = t.byObjectId(id(2))) {
			assertThrows(UnsupportedOperationException.class, () -> rc.seekPastPrefix("refs/heads/"));
		}
	}

	@Test
	public void unpeeledDoesNotWrite() {
		try {
			write(new ObjectIdRef.Unpeeled(PACKED, MASTER, id(1)));
			fail("expected IOException");
		} catch (IOException e) {
			assertEquals(JGitText.get().peeledRefIsRequired, e.getMessage());
		}
	}

	@Test
	public void skipPastRefWithLastUTF8() throws IOException {
		ReftableReader t = read(write(ref(String.format("refs/heads/%sbla", new String(LAST_UTF8_CHAR
				, UTF_8)), 1)));

		try (RefCursor rc = t.allRefs()) {
			rc.seekPastPrefix("refs/heads/");
			assertFalse(rc.next());
		}
	}


	@Test
	public void nameTooLongDoesNotWrite() throws IOException {
		try {
			ReftableConfig cfg = new ReftableConfig();
			cfg.setRefBlockSize(64);

			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			ReftableWriter writer = new ReftableWriter(cfg, buffer).begin();
			writer.writeRef(ref("refs/heads/i-am-not-a-teapot", 1));
			writer.finish();
			fail("expected BlockSizeTooSmallException");
		} catch (BlockSizeTooSmallException e) {
			assertEquals(85, e.getMinimumBlockSize());
		}
	}

	@Test
	public void badCrc32() throws IOException {
		byte[] table = write();
		table[table.length - 1] = 0x42;

		try {
			read(table).seekRef(HEAD);
			fail("expected IOException");
		} catch (IOException e) {
			assertEquals(JGitText.get().invalidReftableCRC, e.getMessage());
		}
	}

	private static void assertScan(List<Ref> refs, Reftable t)
			throws IOException {
		try (RefCursor rc = t.allRefs()) {
			for (Ref exp : refs) {
				assertTrue("has " + exp.getName(), rc.next());
				Ref act = rc.getRef();
				assertEquals(exp.getName(), act.getName());
				assertEquals(exp.getObjectId(), act.getObjectId());
				assertEquals(0, rc.getRef().getUpdateIndex());
			}
			assertFalse(rc.next());
		}
	}

	private static void assertSeek(List<Ref> refs, Reftable t)
			throws IOException {
		for (Ref exp : refs) {
			try (RefCursor rc = t.seekRef(exp.getName())) {
				assertTrue("has " + exp.getName(), rc.next());
				Ref act = rc.getRef();
				assertEquals(exp.getName(), act.getName());
				assertEquals(exp.getObjectId(), act.getObjectId());
				assertEquals(0, rc.getRef().getUpdateIndex());
				assertFalse(rc.next());
			}
		}
	}

	private static Ref ref(String name, int id) {
		return new ObjectIdRef.PeeledNonTag(PACKED, name, id(id));
	}

	private static Ref tag(String name, int id1, int id2) {
		return new ObjectIdRef.PeeledTag(PACKED, name, id(id1), id(id2));
	}

	private static Ref sym(String name, String target) {
		return new SymbolicRef(name, newRef(target));
	}

	private static Ref newRef(String name) {
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

	private byte[] write(Ref... refs) throws IOException {
		return write(Arrays.asList(refs));
	}

	private byte[] write(Collection<Ref> refs) throws IOException {
		return write(refs, new ReftableConfig());
	}

	private byte[] write(Collection<Ref> refs, ReftableConfig cfg) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		stats = new ReftableWriter(buffer)
				.setConfig(cfg)
				.begin()
				.sortAndWriteRefs(refs)
				.finish()
				.getStats();
		return buffer.toByteArray();
	}
}

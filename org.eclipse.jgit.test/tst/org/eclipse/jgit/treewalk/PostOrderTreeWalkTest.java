/*
 * Copyright (C) 2008, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.treewalk;

import static org.eclipse.jgit.lib.FileMode.REGULAR_FILE;
import static org.eclipse.jgit.lib.FileMode.TREE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.FileMode;
import org.junit.Test;

public class PostOrderTreeWalkTest extends RepositoryTestCase {
	@Test
	public void testInitialize_NoPostOrder() throws Exception {
		try (TreeWalk tw = new TreeWalk(db)) {
			assertFalse(tw.isPostOrderTraversal());
		}
	}

	@Test
	public void testInitialize_TogglePostOrder() throws Exception {
		try (TreeWalk tw = new TreeWalk(db)) {
			assertFalse(tw.isPostOrderTraversal());
			tw.setPostOrderTraversal(true);
			assertTrue(tw.isPostOrderTraversal());
			tw.setPostOrderTraversal(false);
			assertFalse(tw.isPostOrderTraversal());
		}
	}

	@Test
	public void testResetDoesNotAffectPostOrder() throws Exception {
		try (TreeWalk tw = new TreeWalk(db)) {
			tw.setPostOrderTraversal(true);
			assertTrue(tw.isPostOrderTraversal());
			tw.reset();
			assertTrue(tw.isPostOrderTraversal());

			tw.setPostOrderTraversal(false);
			assertFalse(tw.isPostOrderTraversal());
			tw.reset();
			assertFalse(tw.isPostOrderTraversal());
		}
	}

	@Test
	public void testNoPostOrder() throws Exception {
		final DirCache tree = db.readDirCache();
		final DirCacheBuilder b = tree.builder();

		b.add(makeFile("a"));
		b.add(makeFile("b/c"));
		b.add(makeFile("b/d"));
		b.add(makeFile("q"));

		b.finish();
		assertEquals(4, tree.getEntryCount());

		try (TreeWalk tw = new TreeWalk(db)) {
			tw.setPostOrderTraversal(false);
			tw.addTree(new DirCacheIterator(tree));

			assertModes("a", REGULAR_FILE, tw);
			assertModes("b", TREE, tw);
			assertTrue(tw.isSubtree());
			assertFalse(tw.isPostChildren());
			tw.enterSubtree();
			assertModes("b/c", REGULAR_FILE, tw);
			assertModes("b/d", REGULAR_FILE, tw);
			assertModes("q", REGULAR_FILE, tw);
		}
	}

	@Test
	public void testWithPostOrder_EnterSubtree() throws Exception {
		final DirCache tree = db.readDirCache();
		final DirCacheBuilder b = tree.builder();

		b.add(makeFile("a"));
		b.add(makeFile("b/c"));
		b.add(makeFile("b/d"));
		b.add(makeFile("q"));

		b.finish();
		assertEquals(4, tree.getEntryCount());

		try (TreeWalk tw = new TreeWalk(db)) {
			tw.setPostOrderTraversal(true);
			tw.addTree(new DirCacheIterator(tree));

			assertModes("a", REGULAR_FILE, tw);

			assertModes("b", TREE, tw);
			assertTrue(tw.isSubtree());
			assertFalse(tw.isPostChildren());
			tw.enterSubtree();
			assertModes("b/c", REGULAR_FILE, tw);
			assertModes("b/d", REGULAR_FILE, tw);

			assertModes("b", TREE, tw);
			assertTrue(tw.isSubtree());
			assertTrue(tw.isPostChildren());

			assertModes("q", REGULAR_FILE, tw);
		}
	}

	@Test
	public void testWithPostOrder_NoEnterSubtree() throws Exception {
		final DirCache tree = db.readDirCache();
		final DirCacheBuilder b = tree.builder();

		b.add(makeFile("a"));
		b.add(makeFile("b/c"));
		b.add(makeFile("b/d"));
		b.add(makeFile("q"));

		b.finish();
		assertEquals(4, tree.getEntryCount());

		try (TreeWalk tw = new TreeWalk(db)) {
			tw.setPostOrderTraversal(true);
			tw.addTree(new DirCacheIterator(tree));

			assertModes("a", REGULAR_FILE, tw);

			assertModes("b", TREE, tw);
			assertTrue(tw.isSubtree());
			assertFalse(tw.isPostChildren());

			assertModes("q", REGULAR_FILE, tw);
		}
	}

	private DirCacheEntry makeFile(String path) throws Exception {
		return createEntry(path, REGULAR_FILE);
	}

	private static void assertModes(final String path, final FileMode mode0,
			final TreeWalk tw) throws Exception {
		assertTrue("has " + path, tw.next());
		assertEquals(path, tw.getPathString());
		assertEquals(mode0, tw.getFileMode(0));
	}
}

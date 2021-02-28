/*
 * Copyright (C) 2011-2018, Chris Aniszczyk <caniszczyk@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static java.time.Instant.EPOCH;
import static org.eclipse.jgit.api.ResetCommand.ResetType.HARD;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Assert;
import org.junit.Test;

public class ResetCommandTest extends RepositoryTestCase {

	private Git git;

	private RevCommit initialCommit;

	private RevCommit secondCommit;

	private File indexFile;

	private File indexNestedFile;

	private File untrackedFile;

	private DirCacheEntry prestage;

	public void setupRepository() throws IOException, JGitInternalException,
			GitAPIException {

		// create initial commit
		git = new Git(db);
		initialCommit = git.commit().setMessage("initial commit").call();

		// create file
		indexFile = writeTrashFile("a.txt", "content");

		// create nested file
		indexNestedFile = writeTrashFile("dir/b.txt", "content");

		// add files and commit them
		git.add().addFilepattern("a.txt").addFilepattern("dir/b.txt").call();
		secondCommit = git.commit().setMessage("adding a.txt and dir/b.txt").call();

		prestage = DirCache.read(db.getIndexFile(), db.getFS()).getEntry(indexFile.getName());

		// modify files and add to index
		writeTrashFile("a.txt", "new content");
		writeTrashFile("dir/b.txt", "new content");
		git.add().addFilepattern("a.txt").addFilepattern("dir/b.txt").call();

		// create a file not added to the index
		untrackedFile = writeTrashFile("notAddedToIndex.txt", "content");
	}

	@Test
	public void testHardReset() throws JGitInternalException,
			AmbiguousObjectException, IOException, GitAPIException {
		setupRepository();
		ObjectId prevHead = db.resolve(Constants.HEAD);
		ResetCommand reset = git.reset();
		assertSameAsHead(reset.setMode(ResetType.HARD)
				.setRef(initialCommit.getName()).call());
		assertFalse("reflog should be enabled", reset.isReflogDisabled());
		// check if HEAD points to initial commit now
		ObjectId head = db.resolve(Constants.HEAD);
		assertEquals(initialCommit, head);
		// check if files were removed
		assertFalse(indexFile.exists());
		assertFalse(indexNestedFile.exists());
		assertTrue(untrackedFile.exists());
		// fileInIndex must no longer be in HEAD and in the index
		String fileInIndexPath = indexFile.getAbsolutePath();
		assertFalse(inHead(fileInIndexPath));
		assertFalse(inIndex(indexFile.getName()));
		assertReflog(prevHead, head);
		assertEquals(prevHead, db.readOrigHead());
	}

	@Test
	public void testHardResetReflogDisabled() throws Exception {
		setupRepository();
		ObjectId prevHead = db.resolve(Constants.HEAD);
		ResetCommand reset = git.reset();
		assertSameAsHead(reset.setMode(ResetType.HARD)
				.setRef(initialCommit.getName()).disableRefLog(true).call());
		assertTrue("reflog should be disabled", reset.isReflogDisabled());
		// check if HEAD points to initial commit now
		ObjectId head = db.resolve(Constants.HEAD);
		assertEquals(initialCommit, head);
		// check if files were removed
		assertFalse(indexFile.exists());
		assertFalse(indexNestedFile.exists());
		assertTrue(untrackedFile.exists());
		// fileInIndex must no longer be in HEAD and in the index
		String fileInIndexPath = indexFile.getAbsolutePath();
		assertFalse(inHead(fileInIndexPath));
		assertFalse(inIndex(indexFile.getName()));
		assertReflogDisabled(head);
		assertEquals(prevHead, db.readOrigHead());
	}

	@Test
	public void testHardResetWithConflicts_OverwriteUntrackedFile() throws Exception {
		setupRepository();

		git.rm().setCached(true).addFilepattern("a.txt").call();
		assertTrue(new File(db.getWorkTree(), "a.txt").exists());

		git.reset().setMode(ResetType.HARD).setRef(Constants.HEAD).call();
		assertTrue(new File(db.getWorkTree(), "a.txt").exists());
		assertEquals("content", read(new File(db.getWorkTree(), "a.txt")));
	}

	@Test
	public void testHardResetWithConflicts_DeleteFileFolderConflict() throws Exception {
		setupRepository();

		writeTrashFile("dir-or-file/c.txt", "content");
		git.add().addFilepattern("dir-or-file/c.txt").call();

		FileUtils.delete(new File(db.getWorkTree(), "dir-or-file"), FileUtils.RECURSIVE);
		writeTrashFile("dir-or-file", "content");

		git.reset().setMode(ResetType.HARD).setRef(Constants.HEAD).call();
		assertFalse(new File(db.getWorkTree(), "dir-or-file").exists());
	}

	@Test
	public void testHardResetWithConflicts_CreateFolder_UnstagedChanges() throws Exception {
		setupRepository();

		writeTrashFile("dir-or-file/c.txt", "content");
		git.add().addFilepattern("dir-or-file/c.txt").call();
		git.commit().setMessage("adding dir-or-file/c.txt").call();

		FileUtils.delete(new File(db.getWorkTree(), "dir-or-file"), FileUtils.RECURSIVE);
		writeTrashFile("dir-or-file", "content");

		// bug 479266: cannot create folder "dir-or-file"
		git.reset().setMode(ResetType.HARD).setRef(Constants.HEAD).call();
		assertTrue(new File(db.getWorkTree(), "dir-or-file/c.txt").exists());
	}

	@Test
	public void testHardResetWithConflicts_DeleteFolder_UnstagedChanges() throws Exception {
		setupRepository();
		ObjectId prevHead = db.resolve(Constants.HEAD);

		writeTrashFile("dir-or-file/c.txt", "content");
		git.add().addFilepattern("dir-or-file/c.txt").call();
		git.commit().setMessage("adding dir-or-file/c.txt").call();

		writeTrashFile("dir-or-file-2/d.txt", "content");
		git.add().addFilepattern("dir-or-file-2/d.txt").call();
		FileUtils.delete(new File(db.getWorkTree(), "dir-or-file-2"), FileUtils.RECURSIVE);
		writeTrashFile("dir-or-file-2", "content");

		// bug 479266: cannot delete folder "dir-or-file"
		git.reset().setMode(ResetType.HARD).setRef(prevHead.getName()).call();
		assertFalse(new File(db.getWorkTree(), "dir-or-file").exists());
		assertFalse(new File(db.getWorkTree(), "dir-or-file-2").exists());
	}

	@Test
	public void testResetToNonexistingHEAD() throws JGitInternalException,
			AmbiguousObjectException, IOException, GitAPIException {

		// create a file in the working tree of a fresh repo
		git = new Git(db);
		writeTrashFile("f", "content");

		try {
			git.reset().setRef(Constants.HEAD).call();
			fail("Expected JGitInternalException didn't occur");
		} catch (JGitInternalException e) {
			// got the expected exception
		}
	}

	@Test
	public void testSoftReset() throws JGitInternalException,
			AmbiguousObjectException, IOException, GitAPIException {
		setupRepository();
		ObjectId prevHead = db.resolve(Constants.HEAD);
		assertSameAsHead(git.reset().setMode(ResetType.SOFT)
				.setRef(initialCommit.getName()).call());
		// check if HEAD points to initial commit now
		ObjectId head = db.resolve(Constants.HEAD);
		assertEquals(initialCommit, head);
		// check if files still exist
		assertTrue(untrackedFile.exists());
		assertTrue(indexFile.exists());
		// fileInIndex must no longer be in HEAD but has to be in the index
		String fileInIndexPath = indexFile.getAbsolutePath();
		assertFalse(inHead(fileInIndexPath));
		assertTrue(inIndex(indexFile.getName()));
		assertReflog(prevHead, head);
		assertEquals(prevHead, db.readOrigHead());
	}

	@Test
	public void testMixedReset() throws JGitInternalException,
			AmbiguousObjectException, IOException, GitAPIException {
		setupRepository();
		ObjectId prevHead = db.resolve(Constants.HEAD);
		assertSameAsHead(git.reset().setMode(ResetType.MIXED)
				.setRef(initialCommit.getName()).call());
		// check if HEAD points to initial commit now
		ObjectId head = db.resolve(Constants.HEAD);
		assertEquals(initialCommit, head);
		// check if files still exist
		assertTrue(untrackedFile.exists());
		assertTrue(indexFile.exists());
		// fileInIndex must no longer be in HEAD and in the index
		String fileInIndexPath = indexFile.getAbsolutePath();
		assertFalse(inHead(fileInIndexPath));
		assertFalse(inIndex(indexFile.getName()));

		assertReflog(prevHead, head);
		assertEquals(prevHead, db.readOrigHead());
	}

	@Test
	public void testMixedResetRetainsSizeAndModifiedTime() throws Exception {
		git = new Git(db);

		Files.setLastModifiedTime(writeTrashFile("a.txt", "a").toPath(),
				FileTime.from(Instant.now().minusSeconds(60)));
		assertNotNull(git.add().addFilepattern("a.txt").call());
		assertNotNull(git.commit().setMessage("a commit").call());

		Files.setLastModifiedTime(writeTrashFile("b.txt", "b").toPath(),
				FileTime.from(Instant.now().minusSeconds(60)));
		assertNotNull(git.add().addFilepattern("b.txt").call());
		RevCommit commit2 = git.commit().setMessage("b commit").call();
		assertNotNull(commit2);

		DirCache cache = db.readDirCache();

		DirCacheEntry aEntry = cache.getEntry("a.txt");
		assertNotNull(aEntry);
		assertTrue(aEntry.getLength() > 0);
		assertTrue(aEntry.getLastModifiedInstant().compareTo(EPOCH) > 0);

		DirCacheEntry bEntry = cache.getEntry("b.txt");
		assertNotNull(bEntry);
		assertTrue(bEntry.getLength() > 0);
		assertTrue(bEntry.getLastModifiedInstant().compareTo(EPOCH) > 0);

		assertSameAsHead(git.reset().setMode(ResetType.MIXED)
				.setRef(commit2.getName()).call());

		cache = db.readDirCache();

		DirCacheEntry mixedAEntry = cache.getEntry("a.txt");
		assertNotNull(mixedAEntry);
		assertEquals(aEntry.getLastModifiedInstant(),
				mixedAEntry.getLastModifiedInstant());
		assertEquals(aEntry.getLastModifiedInstant(),
				mixedAEntry.getLastModifiedInstant());

		DirCacheEntry mixedBEntry = cache.getEntry("b.txt");
		assertNotNull(mixedBEntry);
		assertEquals(bEntry.getLastModifiedInstant(),
				mixedBEntry.getLastModifiedInstant());
		assertEquals(bEntry.getLastModifiedInstant(),
				mixedBEntry.getLastModifiedInstant());
	}

	@Test
	public void testMixedResetWithUnmerged() throws Exception {
		git = new Git(db);

		String file = "a.txt";
		writeTrashFile(file, "data");
		String file2 = "b.txt";
		writeTrashFile(file2, "data");

		git.add().addFilepattern(file).addFilepattern(file2).call();
		git.commit().setMessage("commit").call();

		DirCache index = db.lockDirCache();
		DirCacheBuilder builder = index.builder();
		builder.add(createEntry(file, FileMode.REGULAR_FILE, 1, ""));
		builder.add(createEntry(file, FileMode.REGULAR_FILE, 2, ""));
		builder.add(createEntry(file, FileMode.REGULAR_FILE, 3, ""));
		assertTrue(builder.commit());

		assertEquals("[a.txt, mode:100644, stage:1]"
				+ "[a.txt, mode:100644, stage:2]"
				+ "[a.txt, mode:100644, stage:3]",
				indexState(0));

		assertSameAsHead(git.reset().setMode(ResetType.MIXED).call());

		assertEquals("[a.txt, mode:100644]" + "[b.txt, mode:100644]",
				indexState(0));
	}

	@Test
	public void testPathsReset() throws Exception {
		setupRepository();

		DirCacheEntry preReset = DirCache.read(db.getIndexFile(), db.getFS())
				.getEntry(indexFile.getName());
		assertNotNull(preReset);

		git.add().addFilepattern(untrackedFile.getName()).call();

		// 'a.txt' has already been modified in setupRepository
		// 'notAddedToIndex.txt' has been added to repository
		assertSameAsHead(git.reset().addPath(indexFile.getName())
				.addPath(untrackedFile.getName()).call());

		DirCacheEntry postReset = DirCache.read(db.getIndexFile(), db.getFS())
				.getEntry(indexFile.getName());
		assertNotNull(postReset);
		Assert.assertNotSame(preReset.getObjectId(), postReset.getObjectId());
		Assert.assertEquals(prestage.getObjectId(), postReset.getObjectId());

		// check that HEAD hasn't moved
		ObjectId head = db.resolve(Constants.HEAD);
		assertEquals(secondCommit, head);
		// check if files still exist
		assertTrue(untrackedFile.exists());
		assertTrue(indexFile.exists());
		assertTrue(inHead(indexFile.getName()));
		assertTrue(inIndex(indexFile.getName()));
		assertFalse(inIndex(untrackedFile.getName()));
	}

	@Test
	public void testPathsResetOnDirs() throws Exception {
		setupRepository();

		DirCacheEntry preReset = DirCache.read(db.getIndexFile(), db.getFS())
				.getEntry("dir/b.txt");
		assertNotNull(preReset);

		git.add().addFilepattern(untrackedFile.getName()).call();

		// 'dir/b.txt' has already been modified in setupRepository
		assertSameAsHead(git.reset().addPath("dir").call());

		DirCacheEntry postReset = DirCache.read(db.getIndexFile(), db.getFS())
				.getEntry("dir/b.txt");
		assertNotNull(postReset);
		Assert.assertNotSame(preReset.getObjectId(), postReset.getObjectId());

		// check that HEAD hasn't moved
		ObjectId head = db.resolve(Constants.HEAD);
		assertEquals(secondCommit, head);
		// check if files still exist
		assertTrue(untrackedFile.exists());
		assertTrue(inHead("dir/b.txt"));
		assertTrue(inIndex("dir/b.txt"));
	}

	@Test
	public void testPathsResetWithRef() throws Exception {
		setupRepository();

		DirCacheEntry preReset = DirCache.read(db.getIndexFile(), db.getFS())
				.getEntry(indexFile.getName());
		assertNotNull(preReset);

		git.add().addFilepattern(untrackedFile.getName()).call();

		// 'a.txt' has already been modified in setupRepository
		// 'notAddedToIndex.txt' has been added to repository
		// reset to the inital commit
		assertSameAsHead(git.reset().setRef(initialCommit.getName())
				.addPath(indexFile.getName()).addPath(untrackedFile.getName())
				.call());

		// check that HEAD hasn't moved
		ObjectId head = db.resolve(Constants.HEAD);
		assertEquals(secondCommit, head);
		// check if files still exist
		assertTrue(untrackedFile.exists());
		assertTrue(indexFile.exists());
		assertTrue(inHead(indexFile.getName()));
		assertFalse(inIndex(indexFile.getName()));
		assertFalse(inIndex(untrackedFile.getName()));
	}

	@Test
	public void testPathsResetWithUnmerged() throws Exception {
		setupRepository();

		String file = "a.txt";
		writeTrashFile(file, "data");

		git.add().addFilepattern(file).call();
		git.commit().setMessage("commit").call();

		DirCache index = db.lockDirCache();
		DirCacheBuilder builder = index.builder();
		builder.add(createEntry(file, FileMode.REGULAR_FILE, 1, ""));
		builder.add(createEntry(file, FileMode.REGULAR_FILE, 2, ""));
		builder.add(createEntry(file, FileMode.REGULAR_FILE, 3, ""));
		builder.add(createEntry("b.txt", FileMode.REGULAR_FILE));
		assertTrue(builder.commit());

		assertEquals("[a.txt, mode:100644, stage:1]"
				+ "[a.txt, mode:100644, stage:2]"
				+ "[a.txt, mode:100644, stage:3]"
				+ "[b.txt, mode:100644]",
				indexState(0));

		assertSameAsHead(git.reset().addPath(file).call());

		assertEquals("[a.txt, mode:100644]" + "[b.txt, mode:100644]",
				indexState(0));
	}

	@Test
	public void testPathsResetOnUnbornBranch() throws Exception {
		git = new Git(db);
		writeTrashFile("a.txt", "content");
		git.add().addFilepattern("a.txt").call();
		// Should assume an empty tree, like in C Git 1.8.2
		assertSameAsHead(git.reset().addPath("a.txt").call());

		DirCache cache = db.readDirCache();
		DirCacheEntry aEntry = cache.getEntry("a.txt");
		assertNull(aEntry);
	}

	@Test(expected = JGitInternalException.class)
	public void testPathsResetToNonexistingRef() throws Exception {
		git = new Git(db);
		writeTrashFile("a.txt", "content");
		git.add().addFilepattern("a.txt").call();
		assertSameAsHead(
				git.reset().setRef("doesnotexist").addPath("a.txt").call());
	}

	@Test
	public void testResetDefaultMode() throws Exception {
		git = new Git(db);
		writeTrashFile("a.txt", "content");
		git.add().addFilepattern("a.txt").call();
		writeTrashFile("a.txt", "modified");
		// should use default mode MIXED
		assertSameAsHead(git.reset().call());

		DirCache cache = db.readDirCache();
		DirCacheEntry aEntry = cache.getEntry("a.txt");
		assertNull(aEntry);
		assertEquals("modified", read("a.txt"));
	}

	@Test
	public void testHardResetOnTag() throws Exception {
		setupRepository();
		String tagName = "initialtag";
		git.tag().setName(tagName).setObjectId(secondCommit)
				.setMessage("message").call();

		DirCacheEntry preReset = DirCache.read(db.getIndexFile(), db.getFS())
				.getEntry(indexFile.getName());
		assertNotNull(preReset);

		git.add().addFilepattern(untrackedFile.getName()).call();

		assertSameAsHead(git.reset().setRef(tagName).setMode(HARD).call());

		ObjectId head = db.resolve(Constants.HEAD);
		assertEquals(secondCommit, head);
	}

	@Test
	public void testHardResetAfterSquashMerge() throws Exception {
		git = new Git(db);

		writeTrashFile("file1", "file1");
		git.add().addFilepattern("file1").call();
		RevCommit first = git.commit().setMessage("initial commit").call();

		assertTrue(new File(db.getWorkTree(), "file1").exists());
		createBranch(first, "refs/heads/branch1");
		checkoutBranch("refs/heads/branch1");

		writeTrashFile("file2", "file2");
		git.add().addFilepattern("file2").call();
		git.commit().setMessage("second commit").call();
		assertTrue(new File(db.getWorkTree(), "file2").exists());

		checkoutBranch("refs/heads/master");

		MergeResult result = git.merge()
				.include(db.exactRef("refs/heads/branch1"))
				.setSquash(true)
				.call();

		assertEquals(MergeResult.MergeStatus.FAST_FORWARD_SQUASHED,
				result.getMergeStatus());
		assertNotNull(db.readSquashCommitMsg());

		assertSameAsHead(git.reset().setMode(ResetType.HARD)
				.setRef(first.getName()).call());

		assertNull(db.readSquashCommitMsg());
	}

	@Test
	public void testHardResetOnUnbornBranch() throws Exception {
		git = new Git(db);
		File fileA = writeTrashFile("a.txt", "content");
		git.add().addFilepattern("a.txt").call();
		// Should assume an empty tree, like in C Git 1.8.2
		assertSameAsHead(git.reset().setMode(ResetType.HARD).call());

		DirCache cache = db.readDirCache();
		DirCacheEntry aEntry = cache.getEntry("a.txt");
		assertNull(aEntry);
		assertFalse(fileA.exists());
		assertNull(db.resolve(Constants.HEAD));
	}

	private void assertReflog(ObjectId prevHead, ObjectId head)
			throws IOException {
		// Check the reflog for HEAD
		String actualHeadMessage = db.getReflogReader(Constants.HEAD)
				.getLastEntry().getComment();
		String expectedHeadMessage = head.getName() + ": updating HEAD";
		assertEquals(expectedHeadMessage, actualHeadMessage);
		assertEquals(head.getName(), db.getReflogReader(Constants.HEAD)
				.getLastEntry().getNewId().getName());
		assertEquals(prevHead.getName(), db.getReflogReader(Constants.HEAD)
				.getLastEntry().getOldId().getName());

		// The reflog for master contains the same as the one for HEAD
		String actualMasterMessage = db.getReflogReader("refs/heads/master")
				.getLastEntry().getComment();
		String expectedMasterMessage = head.getName() + ": updating HEAD"; // yes!
		assertEquals(expectedMasterMessage, actualMasterMessage);
		assertEquals(head.getName(), db.getReflogReader(Constants.HEAD)
				.getLastEntry().getNewId().getName());
		assertEquals(prevHead.getName(), db
				.getReflogReader("refs/heads/master").getLastEntry().getOldId()
				.getName());
	}

	private void assertReflogDisabled(ObjectId head)
			throws IOException {
		// Check the reflog for HEAD
		String actualHeadMessage = db.getReflogReader(Constants.HEAD)
				.getLastEntry().getComment();
		String expectedHeadMessage = "commit: adding a.txt and dir/b.txt";
		assertEquals(expectedHeadMessage, actualHeadMessage);
		assertEquals(head.getName(), db.getReflogReader(Constants.HEAD)
				.getLastEntry().getOldId().getName());

		// The reflog for master contains the same as the one for HEAD
		String actualMasterMessage = db.getReflogReader("refs/heads/master")
				.getLastEntry().getComment();
		String expectedMasterMessage = "commit: adding a.txt and dir/b.txt";
		assertEquals(expectedMasterMessage, actualMasterMessage);
		assertEquals(head.getName(), db.getReflogReader(Constants.HEAD)
				.getLastEntry().getOldId().getName());
	}
	/**
	 * Checks if a file with the given path exists in the HEAD tree
	 *
	 * @param path
	 * @return true if the file exists
	 * @throws IOException
	 */
	private boolean inHead(String path) throws IOException {
		ObjectId headId = db.resolve(Constants.HEAD);
		try (RevWalk rw = new RevWalk(db);
				TreeWalk tw = TreeWalk.forPath(db, path,
						rw.parseTree(headId))) {
			return tw != null;
		}
	}

	/**
	 * Checks if a file with the given path exists in the index
	 *
	 * @param path
	 * @return true if the file exists
	 * @throws IOException
	 */
	private boolean inIndex(String path) throws IOException {
		DirCache dc = DirCache.read(db.getIndexFile(), db.getFS());
		return dc.getEntry(path) != null;
	}

	/**
	 * Asserts that a certain ref is similar to repos HEAD.
	 * @param ref
	 * @throws IOException
	 */
	private void assertSameAsHead(Ref ref) throws IOException {
		Ref headRef = db.exactRef(Constants.HEAD);
		assertEquals(headRef.getName(), ref.getName());
		assertEquals(headRef.getObjectId(), ref.getObjectId());
	}
}

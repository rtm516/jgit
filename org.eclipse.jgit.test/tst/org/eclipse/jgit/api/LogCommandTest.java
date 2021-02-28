/*
 * Copyright (C) 2011, GitHub Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jgit.junit.RepositoryTestCase;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.junit.Test;

public class LogCommandTest extends RepositoryTestCase {

	@Test
	public void logAllCommits() throws Exception {
		List<RevCommit> commits = new ArrayList<>();
		Git git = Git.wrap(db);

		writeTrashFile("Test.txt", "Hello world");
		git.add().addFilepattern("Test.txt").call();
		commits.add(git.commit().setMessage("initial commit").call());

		git.branchCreate().setName("branch1").call();
		Ref checkedOut = git.checkout().setName("branch1").call();
		assertEquals("refs/heads/branch1", checkedOut.getName());
		writeTrashFile("Test1.txt", "Hello world!");
		git.add().addFilepattern("Test1.txt").call();
		commits.add(git.commit().setMessage("branch1 commit").call());

		checkedOut = git.checkout().setName("master").call();
		assertEquals("refs/heads/master", checkedOut.getName());
		writeTrashFile("Test2.txt", "Hello world!!");
		git.add().addFilepattern("Test2.txt").call();
		commits.add(git.commit().setMessage("branch1 commit").call());

		Iterator<RevCommit> log = git.log().all().call().iterator();
		assertTrue(log.hasNext());
		assertTrue(commits.contains(log.next()));
		assertTrue(log.hasNext());
		assertTrue(commits.contains(log.next()));
		assertTrue(log.hasNext());
		assertTrue(commits.contains(log.next()));
		assertFalse(log.hasNext());
	}

    @Test
    public void logAllCommitsWithTag() throws Exception {
		List<RevCommit> commits = new ArrayList<>();
		Git git = Git.wrap(db);

		writeTrashFile("Test.txt", "Hello world");
		git.add().addFilepattern("Test.txt").call();
		commits.add(git.commit().setMessage("initial commit").call());

		TagCommand tagCmd = git.tag();
		tagCmd.setName("tagcommit");
		tagCmd.setObjectId(commits.get(0));
		tagCmd.setTagger(new PersonIdent(db));
		Ref tag = tagCmd.call();

		tagCmd = git.tag();
		tagCmd.setName("tagtree");
		tagCmd.setObjectId(commits.get(0).getTree());
		tagCmd.setTagger(new PersonIdent(db));
		tagCmd.call();

		Iterator<RevCommit> log = git.log().all().call().iterator();
		assertTrue(log.hasNext());
		RevCommit commit = log.next();
		tag = db.getRefDatabase().peel(tag);

		assertEquals(commit.getName(), tag.getPeeledObjectId().getName());
		assertTrue(commits.contains(commit));
	}

	private List<RevCommit> createCommits(Git git) throws Exception {
		List<RevCommit> commits = new ArrayList<>();
		writeTrashFile("Test.txt", "Hello world");
		git.add().addFilepattern("Test.txt").call();
		commits.add(git.commit().setMessage("commit#1").call());
		writeTrashFile("Test.txt", "Hello world!");
		git.add().addFilepattern("Test.txt").call();
		commits.add(git.commit().setMessage("commit#2").call());
		writeTrashFile("Test1.txt", "Hello world!!");
		git.add().addFilepattern("Test1.txt").call();
		commits.add(git.commit().setMessage("commit#3").call());
		return commits;
	}

	@Test
	public void logAllCommitsWithMaxCount() throws Exception {
		Git git = Git.wrap(db);
		List<RevCommit> commits = createCommits(git);

		Iterator<RevCommit> log = git.log().all().setMaxCount(2).call()
				.iterator();
		assertTrue(log.hasNext());
		RevCommit commit = log.next();
		assertTrue(commits.contains(commit));
		assertEquals("commit#3", commit.getShortMessage());
		assertTrue(log.hasNext());
		commit = log.next();
		assertTrue(commits.contains(commit));
		assertEquals("commit#2", commit.getShortMessage());
		assertFalse(log.hasNext());
	}

	@Test
	public void logPathWithMaxCount() throws Exception {
		Git git = Git.wrap(db);
		List<RevCommit> commits = createCommits(git);

		Iterator<RevCommit> log = git.log().addPath("Test.txt").setMaxCount(1)
				.call().iterator();
		assertTrue(log.hasNext());
		RevCommit commit = log.next();
		assertTrue(commits.contains(commit));
		assertEquals("commit#2", commit.getShortMessage());
		assertFalse(log.hasNext());
	}

	@Test
	public void logPathWithSkip() throws Exception {
		Git git = Git.wrap(db);
		List<RevCommit> commits = createCommits(git);

		Iterator<RevCommit> log = git.log().addPath("Test.txt").setSkip(1)
				.call().iterator();
		assertTrue(log.hasNext());
		RevCommit commit = log.next();
		assertTrue(commits.contains(commit));
		assertEquals("commit#1", commit.getShortMessage());
		assertFalse(log.hasNext());
	}

	@Test
	public void logAllCommitsWithSkip() throws Exception {
		Git git = Git.wrap(db);
		List<RevCommit> commits = createCommits(git);

		Iterator<RevCommit> log = git.log().all().setSkip(1).call().iterator();
		assertTrue(log.hasNext());
		RevCommit commit = log.next();
		assertTrue(commits.contains(commit));
		assertEquals("commit#2", commit.getShortMessage());
		assertTrue(log.hasNext());
		commit = log.next();
		assertTrue(commits.contains(commit));
		assertEquals("commit#1", commit.getShortMessage());
		assertFalse(log.hasNext());
	}

	@Test
	public void logAllCommitsWithSkipAndMaxCount() throws Exception {
		Git git = Git.wrap(db);
		List<RevCommit> commits = createCommits(git);

		Iterator<RevCommit> log = git.log().all().setSkip(1).setMaxCount(1).call()
				.iterator();
		assertTrue(log.hasNext());
		RevCommit commit = log.next();
		assertTrue(commits.contains(commit));
		assertEquals("commit#2", commit.getShortMessage());
		assertFalse(log.hasNext());
	}

	@Test
	public void logOnlyMergeCommits() throws Exception {
		setCommitsAndMerge();
		Git git = Git.wrap(db);

		Iterable<RevCommit> commits = git.log().all().call();
		Iterator<RevCommit> i = commits.iterator();
		RevCommit commit = i.next();
		assertEquals("merge s0 with m1", commit.getFullMessage());
		commit = i.next();
		assertEquals("s0", commit.getFullMessage());
		commit = i.next();
		assertEquals("m1", commit.getFullMessage());
		commit = i.next();
		assertEquals("m0", commit.getFullMessage());
		assertFalse(i.hasNext());

		commits = git.log().setRevFilter(RevFilter.ONLY_MERGES).call();
		i = commits.iterator();
		commit = i.next();
		assertEquals("merge s0 with m1", commit.getFullMessage());
		assertFalse(i.hasNext());
	}

	@Test
	public void logNoMergeCommits() throws Exception {
		setCommitsAndMerge();
		Git git = Git.wrap(db);

		Iterable<RevCommit> commits = git.log().all().call();
		Iterator<RevCommit> i = commits.iterator();
		RevCommit commit = i.next();
		assertEquals("merge s0 with m1", commit.getFullMessage());
		commit = i.next();
		assertEquals("s0", commit.getFullMessage());
		commit = i.next();
		assertEquals("m1", commit.getFullMessage());
		commit = i.next();
		assertEquals("m0", commit.getFullMessage());
		assertFalse(i.hasNext());

		commits = git.log().setRevFilter(RevFilter.NO_MERGES).call();
		i = commits.iterator();
		commit = i.next();
		assertEquals("m1", commit.getFullMessage());
		commit = i.next();
		assertEquals("s0", commit.getFullMessage());
		commit = i.next();
		assertEquals("m0", commit.getFullMessage());
		assertFalse(i.hasNext());
	}

	/**
	 * <pre>
	 * A - B - C - M
	 *      \     /
	 *        -D(side)
	 * </pre>
	 */
	@Test
	public void addRangeWithMerge() throws Exception{
		String fileA = "fileA";
		String fileB = "fileB";
		Git git = Git.wrap(db);

		writeTrashFile(fileA, fileA);
		git.add().addFilepattern(fileA).call();
		git.commit().setMessage("commit a").call();

		writeTrashFile(fileA, fileA);
		git.add().addFilepattern(fileA).call();
		RevCommit b = git.commit().setMessage("commit b").call();

		writeTrashFile(fileA, fileA);
		git.add().addFilepattern(fileA).call();
		RevCommit c = git.commit().setMessage("commit c").call();

		createBranch(b, "refs/heads/side");
		checkoutBranch("refs/heads/side");

		writeTrashFile(fileB, fileB);
		git.add().addFilepattern(fileB).call();
		RevCommit d = git.commit().setMessage("commit d").call();

		checkoutBranch("refs/heads/master");
		MergeResult m = git.merge().include(d.getId()).call();
		assertEquals(MergeResult.MergeStatus.MERGED, m.getMergeStatus());

		Iterator<RevCommit> rangeLog = git.log().addRange(b.getId(), m.getNewHead()).call().iterator();

		RevCommit commit = rangeLog.next();
		assertEquals(m.getNewHead(), commit.getId());
		commit = rangeLog.next();
		assertEquals(c.getId(), commit.getId());
		commit = rangeLog.next();
		assertEquals(d.getId(), commit.getId());
		assertFalse(rangeLog.hasNext());
	}

	private void setCommitsAndMerge() throws Exception {
		Git git = Git.wrap(db);
		writeTrashFile("file1", "1\n2\n3\n4\n");
		git.add().addFilepattern("file1").call();
		RevCommit masterCommit0 = git.commit().setMessage("m0").call();

		createBranch(masterCommit0, "refs/heads/side");
		checkoutBranch("refs/heads/side");

		writeTrashFile("file2", "1\n2\n3\n4\n5\n6\n7\n8\n");
		git.add().addFilepattern("file2").call();
		RevCommit c = git.commit().setMessage("s0").call();

		checkoutBranch("refs/heads/master");

		writeTrashFile("file3", "1\n2\n");
		git.add().addFilepattern("file3").call();
		git.commit().setMessage("m1").call();

		git.merge().include(c.getId())
				.setStrategy(MergeStrategy.RESOLVE)
				.setMessage("merge s0 with m1").call();
	}

}

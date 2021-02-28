/*
 * Copyright (C) 2016, Christian Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.lfs.server.fs;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lfs.BuiltinLFS;
import org.eclipse.jgit.lfs.lib.LongObjectId;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class CheckoutTest extends LfsServerTest {

	Git git;
	private TestRepository tdb;

	@Override
	@Before
	public void setup() throws Exception {
		super.setup();

		BuiltinLFS.register();

		Path tmp = Files.createTempDirectory("jgit_test_");
		Repository db = FileRepositoryBuilder
				.create(tmp.resolve(".git").toFile());
		db.create();
		StoredConfig cfg = db.getConfig();
		cfg.setBoolean(ConfigConstants.CONFIG_FILTER_SECTION,
				ConfigConstants.CONFIG_SECTION_LFS,
				ConfigConstants.CONFIG_KEY_USEJGITBUILTIN, true);
		cfg.setBoolean(ConfigConstants.CONFIG_FILTER_SECTION,
				ConfigConstants.CONFIG_SECTION_LFS,
				ConfigConstants.CONFIG_KEY_REQUIRED, false);
		cfg.setString(ConfigConstants.CONFIG_SECTION_LFS, null, "url",
				server.getURI().toString() + "/lfs");
		cfg.save();

		tdb = new TestRepository<>(db);
		tdb.branch("test").commit()
				.add(".gitattributes",
						"*.bin filter=lfs diff=lfs merge=lfs -text ")
				.add("a.bin",
						"version https://git-lfs.github.com/spec/v1\noid sha256:8bb0cf6eb9b17d0f7d22b456f121257dc1254e1f01665370476383ea776df414\nsize 7\n")
				.create();
		git = Git.wrap(db);
		tdb.branch("test2").commit().add(".gitattributes",
				"*.bin filter=lfs diff=lfs merge=lfs -text ").create();
	}

	@After
	public void cleanup() throws Exception {
		tdb.getRepository().close();
		FileUtils.delete(tdb.getRepository().getWorkTree(),
				FileUtils.RECURSIVE);
	}

	@Test
	public void testUnknownContent() throws Exception {
		git.checkout().setName("test").call();
		// unknown content. We will see the pointer file
		assertEquals(
				"version https://git-lfs.github.com/spec/v1\noid sha256:8bb0cf6eb9b17d0f7d22b456f121257dc1254e1f01665370476383ea776df414\nsize 7\n",
				JGitTestUtil.read(git.getRepository(), "a.bin"));
		assertEquals("[POST /lfs/objects/batch 200]",
				server.getRequests().toString());
	}

	@Test(expected = JGitInternalException.class)
	public void testUnknownContentRequired() throws Exception {
		StoredConfig cfg = tdb.getRepository().getConfig();
		cfg.setBoolean(ConfigConstants.CONFIG_FILTER_SECTION,
				ConfigConstants.CONFIG_SECTION_LFS,
				ConfigConstants.CONFIG_KEY_REQUIRED, true);
		cfg.save();

		// must throw
		git.checkout().setName("test").call();
	}

	@Test
	public void testKnownContent() throws Exception {
		putContent(
				LongObjectId.fromString(
						"8bb0cf6eb9b17d0f7d22b456f121257dc1254e1f01665370476383ea776df414"),
				"1234567");
		git.checkout().setName("test").call();
		// known content. we will see the actual content of the LFS blob.
		assertEquals(
				"1234567",
				JGitTestUtil.read(git.getRepository(), "a.bin"));
		assertEquals(
				"[PUT /lfs/objects/8bb0cf6eb9b17d0f7d22b456f121257dc1254e1f01665370476383ea776df414 200"
						+ ", POST /lfs/objects/batch 200"
						+ ", GET /lfs/objects/8bb0cf6eb9b17d0f7d22b456f121257dc1254e1f01665370476383ea776df414 200]",
				server.getRequests().toString());

		git.checkout().setName("test2").call();
		assertFalse(JGitTestUtil.check(git.getRepository(), "a.bin"));
		git.checkout().setName("test").call();
		// unknown content. We will see the pointer file
		assertEquals("1234567",
				JGitTestUtil.read(git.getRepository(), "a.bin"));
		assertEquals(3, server.getRequests().size());
	}

}

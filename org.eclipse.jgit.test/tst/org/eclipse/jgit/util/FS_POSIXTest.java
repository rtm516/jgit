/*
 * Copyright (C) 2019, Vishal Devgire <vishaldevgire@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FS_POSIXTest {
	private FileBasedConfig jgitConfig;

	private FileBasedConfig systemConfig;

	private FileBasedConfig userConfig;

	private Path tmp;

	@Before
	public void setUp() throws Exception {
		tmp = Files.createTempDirectory("jgit_test_");

		MockSystemReader mockSystemReader = new MockSystemReader();
		SystemReader.setInstance(mockSystemReader);

		// Measure timer resolution before the test to avoid time critical tests
		// are affected by time needed for measurement.
		// The MockSystemReader must be configured first since we need to use
		// the same one here
		FS.getFileStoreAttributes(tmp.getParent());

		jgitConfig = new FileBasedConfig(new File(tmp.toFile(), "jgitconfig"),
				FS.DETECTED);
		systemConfig = new FileBasedConfig(jgitConfig,
				new File(tmp.toFile(), "systemgitconfig"), FS.DETECTED);
		userConfig = new FileBasedConfig(systemConfig,
				new File(tmp.toFile(), "usergitconfig"), FS.DETECTED);
		// We have to set autoDetach to false for tests, because tests expect to
		// be able to clean up by recursively removing the repository, and
		// background GC might be in the middle of writing or deleting files,
		// which would disrupt this.
		userConfig.setBoolean(ConfigConstants.CONFIG_GC_SECTION, null,
				ConfigConstants.CONFIG_KEY_AUTODETACH, false);
		userConfig.save();
		mockSystemReader.setJGitConfig(jgitConfig);
		mockSystemReader.setSystemGitConfig(systemConfig);
		mockSystemReader.setUserGitConfig(userConfig);
	}

	@After
	public void tearDown() throws IOException {
		SystemReader.setInstance(null);
		FileUtils.delete(tmp.toFile(), FileUtils.RECURSIVE | FileUtils.RETRY);
	}

	@Test
	public void supportsAtomicCreateNewFile_shouldReturnSupportedAsDefault() {
		assertTrue(new FS_POSIX().supportsAtomicCreateNewFile());
	}

	@Test
	public void supportsAtomicCreateNewFile_shouldReturnTrueIfFlagIsSetInUserConfig() {
		setAtomicCreateCreationFlag(userConfig, "true");
		assertTrue(new FS_POSIX().supportsAtomicCreateNewFile());
	}

	@Test
	public void supportsAtomicCreateNewFile_shouldReturnTrueIfFlagIsSetInSystemConfig() {
		setAtomicCreateCreationFlag(systemConfig, "true");
		assertTrue(new FS_POSIX().supportsAtomicCreateNewFile());
	}

	@Test
	public void supportsAtomicCreateNewFile_shouldReturnFalseIfFlagUnsetInUserConfig() {
		setAtomicCreateCreationFlag(userConfig, "false");
		assertFalse(new FS_POSIX().supportsAtomicCreateNewFile());
	}

	@Test
	public void supportsAtomicCreateNewFile_shouldReturnFalseIfFlagUnsetInSystemConfig() {
		setAtomicCreateCreationFlag(systemConfig, "false");
		assertFalse(new FS_POSIX().supportsAtomicCreateNewFile());
	}

	private void setAtomicCreateCreationFlag(FileBasedConfig config,
			String value) {
		config.setString(ConfigConstants.CONFIG_CORE_SECTION, null,
				ConfigConstants.CONFIG_KEY_SUPPORTSATOMICFILECREATION, value);
	}
}

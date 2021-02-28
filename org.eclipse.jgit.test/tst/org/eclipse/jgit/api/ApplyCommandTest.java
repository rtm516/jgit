/*
 * Copyright (C) 2011, 2020 IBM Corporation and others
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
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

import org.eclipse.jgit.api.errors.PatchApplyException;
import org.eclipse.jgit.api.errors.PatchFormatException;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.junit.JGitTestUtil;
import org.eclipse.jgit.junit.RepositoryTestCase;
import org.junit.Test;

public class ApplyCommandTest extends RepositoryTestCase {

	private RawText a;

	private RawText b;

	private ApplyResult init(String name) throws Exception {
		return init(name, true, true);
	}

	private ApplyResult init(final String name, final boolean preExists,
			final boolean postExists) throws Exception {
		try (Git git = new Git(db)) {
			if (preExists) {
				a = new RawText(readFile(name + "_PreImage"));
				write(new File(db.getDirectory().getParent(), name),
						a.getString(0, a.size(), false));

				git.add().addFilepattern(name).call();
				git.commit().setMessage("PreImage").call();
			}

			if (postExists) {
				b = new RawText(readFile(name + "_PostImage"));
			}

			return git.apply().setPatch(getTestResource(name + ".patch"))
					.call();
		}
	}

	@Test
	public void testAddA1() throws Exception {
		ApplyResult result = init("A1", false, true);
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "A1"),
				result.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "A1"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testAddA2() throws Exception {
		ApplyResult result = init("A2", false, true);
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "A2"),
				result.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "A2"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testAddBinary() throws Exception {
		ApplyResult result = init("Binary", false, true);
		assertEquals(1, result.getUpdatedFiles().size());

		File createdFile = new File(db.getWorkTree(), "Binary");
		byte[] out = Files.readAllBytes(createdFile.toPath());
		byte[] ref = readFile("Binary_PostImage");

		JGitTestUtil.assertEquals(out, ref);
	}

	@Test
	public void testAddA3() throws Exception {
		ApplyResult result = init("A3", false, true);
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "A3"),
				result.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "A3"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testAddA1Sub() throws Exception {
		ApplyResult result = init("A1_sub", false, false);
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "sub/A1"),
				result.getUpdatedFiles().get(0));
	}

	@Test
	public void testDeleteD() throws Exception {
		ApplyResult result = init("D", true, false);
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "D"),
				result.getUpdatedFiles().get(0));
		assertFalse(new File(db.getWorkTree(), "D").exists());
	}

	@Test(expected = PatchFormatException.class)
	public void testFailureF1() throws Exception {
		init("F1", true, false);
	}

	@Test(expected = PatchApplyException.class)
	public void testFailureF2() throws Exception {
		init("F2", true, false);
	}

	@Test
	public void testModifyE() throws Exception {
		ApplyResult result = init("E");
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "E"),
				result.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "E"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testModifyW() throws Exception {
		ApplyResult result = init("W");
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "W"),
				result.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "W"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testAddM1() throws Exception {
		ApplyResult result = init("M1", false, true);
		assertEquals(1, result.getUpdatedFiles().size());
		assertTrue(result.getUpdatedFiles().get(0).canExecute());
		checkFile(new File(db.getWorkTree(), "M1"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testModifyM2() throws Exception {
		ApplyResult result = init("M2", true, true);
		assertEquals(1, result.getUpdatedFiles().size());
		assertTrue(result.getUpdatedFiles().get(0).canExecute());
		checkFile(new File(db.getWorkTree(), "M2"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testModifyM3() throws Exception {
		ApplyResult result = init("M3", true, true);
		assertEquals(1, result.getUpdatedFiles().size());
		assertFalse(result.getUpdatedFiles().get(0).canExecute());
		checkFile(new File(db.getWorkTree(), "M3"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testModifyX() throws Exception {
		ApplyResult result = init("X");
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "X"),
				result.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "X"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testModifyY() throws Exception {
		ApplyResult result = init("Y");
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "Y"),
				result.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "Y"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testModifyZ() throws Exception {
		ApplyResult result = init("Z");
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "Z"),
				result.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "Z"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testModifyNL1() throws Exception {
		ApplyResult result = init("NL1");
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "NL1"),
				result.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "NL1"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testNonASCII() throws Exception {
		ApplyResult result = init("NonASCII");
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "NonASCII"),
				result.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "NonASCII"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testNonASCII2() throws Exception {
		ApplyResult result = init("NonASCII2");
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "NonASCII2"),
				result.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "NonASCII2"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testNonASCIIAdd() throws Exception {
		ApplyResult result = init("NonASCIIAdd");
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "NonASCIIAdd"),
				result.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "NonASCIIAdd"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testNonASCIIAdd2() throws Exception {
		ApplyResult result = init("NonASCIIAdd2", false, true);
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "NonASCIIAdd2"),
				result.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "NonASCIIAdd2"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testNonASCIIDel() throws Exception {
		ApplyResult result = init("NonASCIIDel", true, false);
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "NonASCIIDel"),
				result.getUpdatedFiles().get(0));
		assertFalse(new File(db.getWorkTree(), "NonASCIIDel").exists());
	}

	@Test
	public void testRenameNoHunks() throws Exception {
		ApplyResult result = init("RenameNoHunks", true, true);
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "RenameNoHunks"), result.getUpdatedFiles()
				.get(0));
		checkFile(new File(db.getWorkTree(), "nested/subdir/Renamed"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testRenameWithHunks() throws Exception {
		ApplyResult result = init("RenameWithHunks", true, true);
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "RenameWithHunks"), result.getUpdatedFiles()
				.get(0));
		checkFile(new File(db.getWorkTree(), "nested/subdir/Renamed"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testCopyWithHunks() throws Exception {
		ApplyResult result = init("CopyWithHunks", true, true);
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "CopyWithHunks"), result.getUpdatedFiles()
				.get(0));
		checkFile(new File(db.getWorkTree(), "CopyResult"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testShiftUp() throws Exception {
		ApplyResult result = init("ShiftUp");
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "ShiftUp"),
				result.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "ShiftUp"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testShiftUp2() throws Exception {
		ApplyResult result = init("ShiftUp2");
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "ShiftUp2"),
				result.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "ShiftUp2"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testShiftDown() throws Exception {
		ApplyResult result = init("ShiftDown");
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "ShiftDown"),
				result.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "ShiftDown"),
				b.getString(0, b.size(), false));
	}

	@Test
	public void testShiftDown2() throws Exception {
		ApplyResult result = init("ShiftDown2");
		assertEquals(1, result.getUpdatedFiles().size());
		assertEquals(new File(db.getWorkTree(), "ShiftDown2"),
				result.getUpdatedFiles().get(0));
		checkFile(new File(db.getWorkTree(), "ShiftDown2"),
				b.getString(0, b.size(), false));
	}

	private static byte[] readFile(String patchFile) throws IOException {
		final InputStream in = getTestResource(patchFile);
		if (in == null) {
			fail("No " + patchFile + " test vector");
			return null; // Never happens
		}
		try {
			final byte[] buf = new byte[1024];
			final ByteArrayOutputStream temp = new ByteArrayOutputStream();
			int n;
			while ((n = in.read(buf)) > 0)
				temp.write(buf, 0, n);
			return temp.toByteArray();
		} finally {
			in.close();
		}
	}

	private static InputStream getTestResource(String patchFile) {
		return ApplyCommandTest.class.getClassLoader()
				.getResourceAsStream("org/eclipse/jgit/diff/" + patchFile);
	}
}

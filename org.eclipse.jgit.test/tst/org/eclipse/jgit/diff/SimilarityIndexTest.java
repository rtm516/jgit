/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.diff;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.eclipse.jgit.diff.SimilarityIndex.TableFullException;
import org.eclipse.jgit.lib.Constants;
import org.junit.Test;

public class SimilarityIndexTest {
	@Test
	public void testIndexingSmallObject() throws TableFullException {
		SimilarityIndex si = hash("" //
				+ "A\n" //
				+ "B\n" //
				+ "D\n" //
				+ "B\n" //
		);

		int key_A = keyFor("A\n");
		int key_B = keyFor("B\n");
		int key_D = keyFor("D\n");
		assertTrue(key_A != key_B && key_A != key_D && key_B != key_D);

		assertEquals(3, si.size());
		assertEquals(2, si.count(si.findIndex(key_A)));
		assertEquals(4, si.count(si.findIndex(key_B)));
		assertEquals(2, si.count(si.findIndex(key_D)));
	}

	@Test
	public void testIndexingLargeObject() throws IOException,
			TableFullException {
		byte[] in = ("" //
				+ "A\n" //
				+ "B\n" //
				+ "B\n" //
				+ "B\n").getBytes(UTF_8);
		SimilarityIndex si = new SimilarityIndex();
		si.hash(new ByteArrayInputStream(in), in.length, false);
		assertEquals(2, si.size());
	}

	@Test
	public void testCommonScore_SameFiles() throws TableFullException {
		String text = "" //
				+ "A\n" //
				+ "B\n" //
				+ "D\n" //
				+ "B\n";
		SimilarityIndex src = hash(text);
		SimilarityIndex dst = hash(text);
		assertEquals(8, src.common(dst));
		assertEquals(8, dst.common(src));

		assertEquals(100, src.score(dst, 100));
		assertEquals(100, dst.score(src, 100));
	}

	@Test
	public void testCommonScore_SameFiles_CR_canonicalization()
			throws TableFullException {
		String text = "" //
				+ "A\r\n" //
				+ "B\r\n" //
				+ "D\r\n" //
				+ "B\r\n";
		SimilarityIndex src = hash(text);
		SimilarityIndex dst = hash(text.replace("\r", ""));
		assertEquals(8, src.common(dst));
		assertEquals(8, dst.common(src));

		assertEquals(100, src.score(dst, 100));
		assertEquals(100, dst.score(src, 100));
	}

	@Test
	public void testCommonScoreLargeObject_SameFiles_CR_canonicalization()
			throws TableFullException, IOException {
		String text = "" //
				+ "A\r\n" //
				+ "B\r\n" //
				+ "D\r\n" //
				+ "B\r\n";
		SimilarityIndex src = new SimilarityIndex();
		byte[] bytes1 = text.getBytes(UTF_8);
		src.hash(new ByteArrayInputStream(bytes1), bytes1.length, true);
		src.sort();

		SimilarityIndex dst = new SimilarityIndex();
		byte[] bytes2 = text.replace("\r", "").getBytes(UTF_8);
		dst.hash(new ByteArrayInputStream(bytes2), bytes2.length, true);
		dst.sort();

		assertEquals(8, src.common(dst));
		assertEquals(8, dst.common(src));

		assertEquals(100, src.score(dst, 100));
		assertEquals(100, dst.score(src, 100));
	}

	@Test
	public void testCommonScore_EmptyFiles() throws TableFullException {
		SimilarityIndex src = hash("");
		SimilarityIndex dst = hash("");
		assertEquals(0, src.common(dst));
		assertEquals(0, dst.common(src));
	}

	@Test
	public void testCommonScore_TotallyDifferentFiles()
			throws TableFullException {
		SimilarityIndex src = hash("A\n");
		SimilarityIndex dst = hash("D\n");
		assertEquals(0, src.common(dst));
		assertEquals(0, dst.common(src));
	}

	@Test
	public void testCommonScore_SimiliarBy75() throws TableFullException {
		SimilarityIndex src = hash("A\nB\nC\nD\n");
		SimilarityIndex dst = hash("A\nB\nC\nQ\n");
		assertEquals(6, src.common(dst));
		assertEquals(6, dst.common(src));

		assertEquals(75, src.score(dst, 100));
		assertEquals(75, dst.score(src, 100));
	}

	private static SimilarityIndex hash(String text) throws TableFullException {
		SimilarityIndex src = new SimilarityIndex();
		byte[] raw = Constants.encode(text);
		src.hash(raw, 0, raw.length);
		src.sort();
		return src;
	}

	private static int keyFor(String line) throws TableFullException {
		SimilarityIndex si = hash(line);
		assertEquals("single line scored", 1, si.size());
		return si.key(0);
	}
}

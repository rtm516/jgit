/*
 * Copyright (C) 2008-2009, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

import org.eclipse.jgit.errors.BinaryBlobException;
import org.junit.Test;

public class RawParseUtils_LineMapTest {

	@Test
	public void testEmpty() throws Exception {
		final IntList map = RawParseUtils.lineMap(new byte[] {}, 0, 0);
		assertNotNull(map);
		assertArrayEquals(new int[]{Integer.MIN_VALUE, 0}, asInts(map));
	}

	@Test
	public void testOneBlankLine() throws Exception  {
		final IntList map = RawParseUtils.lineMap(new byte[] { '\n' }, 0, 1);
		assertArrayEquals(new int[]{Integer.MIN_VALUE, 0, 1}, asInts(map));
	}

	@Test
	public void testTwoLineFooBar() {
		final byte[] buf = "foo\nbar\n".getBytes(ISO_8859_1);
		final IntList map = RawParseUtils.lineMap(buf, 0, buf.length);
		assertArrayEquals(new int[]{Integer.MIN_VALUE, 0, 4, buf.length}, asInts(map));
	}

	@Test
	public void testTwoLineNoLF() {
		final byte[] buf = "foo\nbar".getBytes(ISO_8859_1);
		final IntList map = RawParseUtils.lineMap(buf, 0, buf.length);
		assertArrayEquals(new int[]{Integer.MIN_VALUE, 0, 4, buf.length}, asInts(map));
	}

	@Test
	public void testNulByte() {
		final byte[] buf = "xxxfoo\nb\0ar".getBytes(ISO_8859_1);
		final IntList map = RawParseUtils.lineMap(buf, 3, buf.length);
		assertArrayEquals(new int[] { Integer.MIN_VALUE, 3, 7, buf.length },
				asInts(map));
	}

	@Test
	public void testLineMapOrBinary() throws Exception {
		final byte[] buf = "xxxfoo\nb\0ar".getBytes(ISO_8859_1);
		assertThrows(BinaryBlobException.class,
				() -> RawParseUtils.lineMapOrBinary(buf, 3, buf.length));
	}

	@Test
	public void testFourLineBlanks() {
		final byte[] buf = "foo\n\n\nbar\n".getBytes(ISO_8859_1);
		final IntList map = RawParseUtils.lineMap(buf, 0, buf.length);

		assertArrayEquals(new int[]{
				Integer.MIN_VALUE, 0, 4, 5, 6, buf.length
		}, asInts(map));
	}

	private int[] asInts(IntList l) {
		int[] result = new int[l.size()];
		for (int i = 0; i < l.size(); i++) {
			result[i] = l.get(i);
		}
		return result;
	}
}

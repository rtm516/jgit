/*
 * Copyright (C) 2008, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.util;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static org.eclipse.jgit.util.QuotedString.GIT_PATH;
import static org.eclipse.jgit.util.QuotedString.GIT_PATH_MINIMAL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import org.eclipse.jgit.lib.Constants;
import org.junit.Test;

public class QuotedStringGitPathStyleTest {
	private static void assertQuote(String exp, String in) {
		final String r = GIT_PATH.quote(in);
		assertNotSame(in, r);
		assertFalse(in.equals(r));
		assertEquals('"' + exp + '"', r);
	}

	private static void assertDequote(String exp, String in) {
		final byte[] b = ('"' + in + '"').getBytes(ISO_8859_1);
		final String r = GIT_PATH.dequote(b, 0, b.length);
		assertEquals(exp, r);
	}

	private static void assertDequoteMinimal(String exp, String in) {
		final byte[] b = ('"' + in + '"').getBytes(ISO_8859_1);
		final String r = GIT_PATH_MINIMAL.dequote(b, 0, b.length);
		assertEquals(exp, r);
	}

	@Test
	public void testQuote_Empty() {
		assertEquals("\"\"", GIT_PATH.quote(""));
	}

	@Test
	public void testDequote_Empty1() {
		assertEquals("", GIT_PATH.dequote(new byte[0], 0, 0));
	}

	@Test
	public void testDequote_Empty2() {
		assertEquals("", GIT_PATH.dequote(new byte[] { '"', '"' }, 0, 2));
	}

	@Test
	public void testDequote_SoleDq() {
		assertEquals("\"", GIT_PATH.dequote(new byte[] { '"' }, 0, 1));
	}

	@Test
	public void testQuote_BareA() {
		final String in = "a";
		assertSame(in, GIT_PATH.quote(in));
	}

	@Test
	public void testDequote_BareA() {
		final String in = "a";
		final byte[] b = Constants.encode(in);
		assertEquals(in, GIT_PATH.dequote(b, 0, b.length));
	}

	@Test
	public void testDequote_BareABCZ_OnlyBC() {
		final String in = "abcz";
		final byte[] b = Constants.encode(in);
		final int p = in.indexOf('b');
		assertEquals("bc", GIT_PATH.dequote(b, p, p + 2));
	}

	@Test
	public void testDequote_LoneBackslash() {
		assertDequote("\\", "\\");
	}

	@Test
	public void testQuote_NamedEscapes() {
		assertQuote("\\a", "\u0007");
		assertQuote("\\b", "\b");
		assertQuote("\\f", "\f");
		assertQuote("\\n", "\n");
		assertQuote("\\r", "\r");
		assertQuote("\\t", "\t");
		assertQuote("\\v", "\u000B");
		assertQuote("\\\\", "\\");
		assertQuote("\\\"", "\"");
	}

	@Test
	public void testDequote_NamedEscapes() {
		assertDequote("\u0007", "\\a");
		assertDequote("\b", "\\b");
		assertDequote("\f", "\\f");
		assertDequote("\n", "\\n");
		assertDequote("\r", "\\r");
		assertDequote("\t", "\\t");
		assertDequote("\u000B", "\\v");
		assertDequote("\\", "\\\\");
		assertDequote("\"", "\\\"");
	}

	@Test
	public void testDequote_OctalAll() {
		for (int i = 0; i < 127; i++) {
			assertDequote("" + (char) i, octalEscape(i));
		}
		for (int i = 128; i < 256; i++) {
			int f = 0xC0 | (i >> 6);
			int s = 0x80 | (i & 0x3f);
			assertDequote("" + (char) i, octalEscape(f)+octalEscape(s));
		}
	}

	private static String octalEscape(int i) {
		String s = Integer.toOctalString(i);
		while (s.length() < 3) {
			s = "0" + s;
		}
		return "\\"+s;
	}

	@Test
	public void testQuote_OctalAll() {
		assertQuote("\\001", "\1");
		assertQuote("\\177", "\u007f");
		assertQuote("\\303\\277", "\u00ff"); // \u00ff in UTF-8
	}

	@Test
	public void testDequote_UnknownEscapeQ() {
		assertDequote("\\q", "\\q");
	}

	@Test
	public void testDequote_FooTabBar() {
		assertDequote("foo\tbar", "foo\\tbar");
	}

	@Test
	public void testDequote_Latin1() {
		assertDequote("\u00c5ngstr\u00f6m", "\\305ngstr\\366m"); // Latin1
	}

	@Test
	public void testDequote_UTF8() {
		assertDequote("\u00c5ngstr\u00f6m", "\\303\\205ngstr\\303\\266m");
	}

	@Test
	public void testDequote_RawUTF8() {
		assertDequote("\u00c5ngstr\u00f6m", "\303\205ngstr\303\266m");
	}

	@Test
	public void testDequote_RawLatin1() {
		assertDequote("\u00c5ngstr\u00f6m", "\305ngstr\366m");
	}

	@Test
	public void testQuote_Ang() {
		assertQuote("\\303\\205ngstr\\303\\266m", "\u00c5ngstr\u00f6m");
	}

	@Test
	public void testQuoteAtAndNumber() {
		assertSame("abc@2x.png", GIT_PATH.quote("abc@2x.png"));
		assertDequote("abc@2x.png", "abc\\1002x.png");
	}

	@Test
	public void testNoQuote() {
		assertSame("\u00c5ngstr\u00f6m",
				GIT_PATH_MINIMAL.quote("\u00c5ngstr\u00f6m"));
	}

	@Test
	public void testQuoteMinimal() {
		assertEquals("\"\u00c5n\\\\str\u00f6m\"",
				GIT_PATH_MINIMAL.quote("\u00c5n\\str\u00f6m"));
	}

	@Test
	public void testDequoteMinimal() {
		assertEquals("\u00c5n\\str\u00f6m", GIT_PATH_MINIMAL
				.dequote(GIT_PATH_MINIMAL.quote("\u00c5n\\str\u00f6m")));

	}

	@Test
	public void testRoundtripMinimal() {
		assertEquals("\u00c5ngstr\u00f6m", GIT_PATH_MINIMAL
				.dequote(GIT_PATH_MINIMAL.quote("\u00c5ngstr\u00f6m")));

	}

	@Test
	public void testQuoteMinimalDequoteNormal() {
		assertEquals("\u00c5n\\str\u00f6m", GIT_PATH
				.dequote(GIT_PATH_MINIMAL.quote("\u00c5n\\str\u00f6m")));

	}

	@Test
	public void testQuoteNormalDequoteMinimal() {
		assertEquals("\u00c5n\\str\u00f6m", GIT_PATH_MINIMAL
				.dequote(GIT_PATH.quote("\u00c5n\\str\u00f6m")));

	}

	@Test
	public void testRoundtripMinimalDequoteNormal() {
		assertEquals("\u00c5ngstr\u00f6m",
				GIT_PATH.dequote(GIT_PATH_MINIMAL.quote("\u00c5ngstr\u00f6m")));

	}

	@Test
	public void testRoundtripNormalDequoteMinimal() {
		assertEquals("\u00c5ngstr\u00f6m",
				GIT_PATH_MINIMAL.dequote(GIT_PATH.quote("\u00c5ngstr\u00f6m")));

	}

	@Test
	public void testDequote_UTF8_Minimal() {
		assertDequoteMinimal("\u00c5ngstr\u00f6m",
				"\\303\\205ngstr\\303\\266m");
	}

	@Test
	public void testDequote_RawUTF8_Minimal() {
		assertDequoteMinimal("\u00c5ngstr\u00f6m", "\303\205ngstr\303\266m");
	}

	@Test
	public void testDequote_RawLatin1_Minimal() {
		assertDequoteMinimal("\u00c5ngstr\u00f6m", "\305ngstr\366m");
	}

}

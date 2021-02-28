/*
 * Copyright (C) 2009, Google Inc.
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm.opt;

import java.io.IOException;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

/**
 * Custom argument handler {@link org.eclipse.jgit.revwalk.RevCommit} from
 * string values.
 * <p>
 * Assumes the parser has been initialized with a Repository.
 */
public class RevCommitHandler extends OptionHandler<RevCommit> {
	private final org.eclipse.jgit.pgm.opt.CmdLineParser clp;

	/**
	 * Create a new handler for the command name.
	 * <p>
	 * This constructor is used only by args4j.
	 *
	 * @param parser
	 *            a {@link org.kohsuke.args4j.CmdLineParser} object.
	 * @param option
	 *            a {@link org.kohsuke.args4j.OptionDef} object.
	 * @param setter
	 *            a {@link org.kohsuke.args4j.spi.Setter} object.
	 */
	public RevCommitHandler(final CmdLineParser parser, final OptionDef option,
			final Setter<? super RevCommit> setter) {
		super(parser, option, setter);
		clp = (org.eclipse.jgit.pgm.opt.CmdLineParser) parser;
	}

	/** {@inheritDoc} */
	@Override
	public int parseArguments(Parameters params) throws CmdLineException {
		String name = params.getParameter(0);

		boolean interesting = true;
		if (name.startsWith("^")) { //$NON-NLS-1$
			name = name.substring(1);
			interesting = false;
		}

		final int dot2 = name.indexOf(".."); //$NON-NLS-1$
		if (dot2 != -1) {
			if (!option.isMultiValued())
				throw new CmdLineException(clp,
						CLIText.format(CLIText.get().onlyOneMetaVarExpectedIn),
						option.metaVar(), name);

			final String left = name.substring(0, dot2);
			final String right = name.substring(dot2 + 2);
			addOne(left, false);
			addOne(right, true);
			return 1;
		}

		addOne(name, interesting);
		return 1;
	}

	private void addOne(String name, boolean interesting)
			throws CmdLineException {
		final ObjectId id;
		try {
			id = clp.getRepository().resolve(name);
		} catch (IOException e) {
			throw new CmdLineException(clp, CLIText.format(e.getMessage()));
		}
		if (id == null)
			throw new CmdLineException(clp,
					CLIText.format(CLIText.get().notACommit), name);

		final RevCommit c;
		try {
			c = clp.getRevWalk().parseCommit(id);
		} catch (MissingObjectException | IncorrectObjectTypeException e) {
			CmdLineException cle = new CmdLineException(clp,
					CLIText.format(CLIText.get().notACommit), name);
			cle.initCause(e);
			throw cle;
		} catch (IOException e) {
			throw new CmdLineException(clp,
					CLIText.format(CLIText.get().cannotReadBecause), name,
					e.getMessage());
		}

		if (interesting)
			c.remove(RevFlag.UNINTERESTING);
		else
			c.add(RevFlag.UNINTERESTING);

		setter.addValue(c);
	}

	/** {@inheritDoc} */
	@Override
	public String getDefaultMetaVariable() {
		return CLIText.get().metaVar_commitish;
	}
}

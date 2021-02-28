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
import org.eclipse.jgit.revwalk.RevTree;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.OptionDef;
import org.kohsuke.args4j.spi.OptionHandler;
import org.kohsuke.args4j.spi.Parameters;
import org.kohsuke.args4j.spi.Setter;

/**
 * Custom argument handler {@link org.eclipse.jgit.revwalk.RevTree} from string
 * values.
 * <p>
 * Assumes the parser has been initialized with a Repository.
 */
public class RevTreeHandler extends OptionHandler<RevTree> {
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
	public RevTreeHandler(final CmdLineParser parser, final OptionDef option,
			final Setter<? super RevTree> setter) {
		super(parser, option, setter);
		clp = (org.eclipse.jgit.pgm.opt.CmdLineParser) parser;
	}

	/** {@inheritDoc} */
	@Override
	public int parseArguments(Parameters params) throws CmdLineException {
		final String name = params.getParameter(0);
		final ObjectId id;
		try {
			id = clp.getRepository().resolve(name);
		} catch (IOException e) {
			throw new CmdLineException(clp, CLIText.format(e.getMessage()));
		}
		if (id == null)
			throw new CmdLineException(clp,
					CLIText.format(CLIText.get().notATree), name);

		final RevTree c;
		try {
			c = clp.getRevWalk().parseTree(id);
		} catch (MissingObjectException | IncorrectObjectTypeException e) {
			CmdLineException cle = new CmdLineException(clp,
					CLIText.format(CLIText.get().notATree), name);
			cle.initCause(e);
			throw cle;
		} catch (IOException e) {
			throw new CmdLineException(clp,
					CLIText.format(CLIText.get().cannotReadBecause), name,
					e.getMessage());
		}
		setter.addValue(c);
		return 1;
	}

	/** {@inheritDoc} */
	@Override
	public String getDefaultMetaVariable() {
		return CLIText.get().metaVar_treeish;
	}
}

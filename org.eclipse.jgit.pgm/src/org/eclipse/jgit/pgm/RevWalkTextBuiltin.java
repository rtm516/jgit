/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.pgm;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import org.eclipse.jgit.diff.DiffConfig;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.pgm.internal.CLIText;
import org.eclipse.jgit.pgm.opt.PathTreeFilterHandler;
import org.eclipse.jgit.revwalk.FollowFilter;
import org.eclipse.jgit.revwalk.ObjectWalk;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.AndRevFilter;
import org.eclipse.jgit.revwalk.filter.AuthorRevFilter;
import org.eclipse.jgit.revwalk.filter.CommitterRevFilter;
import org.eclipse.jgit.revwalk.filter.MessageRevFilter;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.treewalk.filter.AndTreeFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

abstract class RevWalkTextBuiltin extends TextBuiltin {
	RevWalk walk;

	@Option(name = "--objects")
	boolean objects = false;

	@Option(name = "--parents")
	boolean parents = false;

	@Option(name = "--total-count")
	boolean count = false;

	@Option(name = "--all")
	boolean all = false;

	char[] outbuffer = new char[Constants.OBJECT_ID_LENGTH * 2];

	private final EnumSet<RevSort> sorting = EnumSet.noneOf(RevSort.class);

	private void enableRevSort(RevSort type, boolean on) {
		if (on)
			sorting.add(type);
		else
			sorting.remove(type);
	}

	@Option(name = "--date-order")
	void enableDateOrder(boolean on) {
		enableRevSort(RevSort.COMMIT_TIME_DESC, on);
	}

	@Option(name = "--topo-order")
	void enableTopoOrder(boolean on) {
		enableRevSort(RevSort.TOPO, on);
	}

	@Option(name = "--reverse")
	void enableReverse(boolean on) {
		enableRevSort(RevSort.REVERSE, on);
	}

	@Option(name = "--boundary")
	void enableBoundary(boolean on) {
		enableRevSort(RevSort.BOUNDARY, on);
	}

	@Option(name = "--follow", metaVar = "metaVar_path")
	private String followPath;

	@Argument(index = 0, metaVar = "metaVar_commitish")
	private List<RevCommit> commits = new ArrayList<>();

	@Option(name = "--", metaVar = "metaVar_path", handler = PathTreeFilterHandler.class)
	protected TreeFilter pathFilter = TreeFilter.ALL;

	private final List<RevFilter> revLimiter = new ArrayList<>();

	@Option(name = "--author")
	void addAuthorRevFilter(String who) {
		revLimiter.add(AuthorRevFilter.create(who));
	}

	@Option(name = "--committer")
	void addCommitterRevFilter(String who) {
		revLimiter.add(CommitterRevFilter.create(who));
	}

	@Option(name = "--grep")
	void addCMessageRevFilter(String msg) {
		revLimiter.add(MessageRevFilter.create(msg));
	}

	@Option(name = "--max-count", aliases = "-n", metaVar = "metaVar_n")
	private int maxCount = -1;

	/** {@inheritDoc} */
	@Override
	protected void run() throws Exception {
		walk = createWalk();
		for (RevSort s : sorting)
			walk.sort(s, true);

		if (pathFilter == TreeFilter.ALL) {
			if (followPath != null)
				walk.setTreeFilter(FollowFilter.create(followPath,
						db.getConfig().get(DiffConfig.KEY)));
		} else if (pathFilter != TreeFilter.ALL) {
			walk.setTreeFilter(AndTreeFilter.create(pathFilter,
					TreeFilter.ANY_DIFF));
		}

		if (revLimiter.size() == 1)
			walk.setRevFilter(revLimiter.get(0));
		else if (revLimiter.size() > 1)
			walk.setRevFilter(AndRevFilter.create(revLimiter));

		if (all) {
			for (Ref a : db.getRefDatabase().getRefs()) {
				ObjectId oid = a.getPeeledObjectId();
				if (oid == null)
					oid = a.getObjectId();
				try {
					commits.add(walk.parseCommit(oid));
				} catch (IncorrectObjectTypeException e) {
					// Ignore all refs which are not commits
				}
			}
		}

		if (commits.isEmpty()) {
			final ObjectId head = db.resolve(Constants.HEAD);
			if (head == null)
				throw die(MessageFormat.format(CLIText.get().cannotResolve, Constants.HEAD));
			commits.add(walk.parseCommit(head));
		}
		for (RevCommit c : commits) {
			final RevCommit real = argWalk == walk ? c : walk.parseCommit(c);
			if (c.has(RevFlag.UNINTERESTING))
				walk.markUninteresting(real);
			else
				walk.markStart(real);
		}

		final long start = System.currentTimeMillis();
		final int n = walkLoop();
		if (count) {
			final long end = System.currentTimeMillis();
			errw.print(n);
			errw.print(' ');
			errw.println(MessageFormat.format(
							CLIText.get().timeInMilliSeconds,
							Long.valueOf(end - start)));
		}
	}

	/**
	 * Create RevWalk
	 *
	 * @return a {@link org.eclipse.jgit.revwalk.RevWalk} object.
	 */
	protected RevWalk createWalk() {
		RevWalk result;
		if (objects)
			result = new ObjectWalk(db);
		else if (argWalk != null)
			result = argWalk;
		else
		  result = argWalk = new RevWalk(db);
		result.setRewriteParents(false);
		return result;
	}

	/**
	 * Loop the walk
	 *
	 * @return number of RevCommits walked
	 * @throws java.lang.Exception
	 *             if any.
	 */
	protected int walkLoop() throws Exception {
		int n = 0;
		for (RevCommit c : walk) {
			if (++n > maxCount && maxCount >= 0)
				break;
			show(c);
		}
		if (walk instanceof ObjectWalk) {
			final ObjectWalk ow = (ObjectWalk) walk;
			for (;;) {
				final RevObject obj = ow.nextObject();
				if (obj == null)
					break;
				show(ow, obj);
			}
		}
		return n;
	}

	/**
	 * "Show" the current RevCommit when called from the main processing loop.
	 * <p>
	 * Implement this methods to define the behavior for subclasses of
	 * RevWalkTextBuiltin.
	 *
	 * @param c
	 *            The current {@link org.eclipse.jgit.revwalk.RevCommit}
	 * @throws java.lang.Exception
	 */
	protected abstract void show(RevCommit c) throws Exception;

	/**
	 * "Show" the current RevCommit when called from the main processing loop.
	 * <p>
	 * The default implementation does nothing because most subclasses only
	 * process RevCommits.
	 *
	 * @param objectWalk
	 *            the {@link org.eclipse.jgit.revwalk.ObjectWalk} used by
	 *            {@link #walkLoop()}
	 * @param currentObject
	 *            The current {@link org.eclipse.jgit.revwalk.RevObject}
	 * @throws java.lang.Exception
	 */
	protected void show(final ObjectWalk objectWalk,
			final RevObject currentObject) throws Exception {
		// Do nothing by default. Most applications cannot show an object.
	}
}

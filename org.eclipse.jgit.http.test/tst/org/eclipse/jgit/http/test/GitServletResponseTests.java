/*
 * Copyright (C) 2015, christian.Halstrick <christian.halstrick@sap.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.http.test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collection;
import java.util.Collections;

import javax.servlet.http.HttpServletRequest;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.errors.TooLargePackException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.http.server.resolver.DefaultReceivePackFactory;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.http.HttpTestCase;
import org.eclipse.jgit.lib.AnyObjectId;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectChecker;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.PreReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for correct responses of {@link GitServlet}. Especially error
 * situations where the {@link GitServlet} faces exceptions during request
 * processing are tested
 */
public class GitServletResponseTests extends HttpTestCase {
	private Repository srvRepo;

	private URIish srvURI;

	private GitServlet gs;

	private long maxPackSize = 0; // the maximum pack file size used by
									// the server

	private PostReceiveHook postHook = null;

	private PreReceiveHook preHook = null;

	private ObjectChecker oc = null;

	/**
	 * Setup a http server using {@link GitServlet}. Tests should be able to
	 * configure the maximum pack file size, the object checker and custom hooks
	 * just before they talk to the server.
	 */
	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();

		final TestRepository<Repository> srv = createTestRepository();
		final String repoName = srv.getRepository().getDirectory().getName();

		ServletContextHandler app = server.addContext("/git");
		gs = new GitServlet();
		gs.setRepositoryResolver((HttpServletRequest req, String name) -> {
			if (!name.equals(repoName)) {
				throw new RepositoryNotFoundException(name);
			}
			final Repository db = srv.getRepository();
			db.incrementOpen();
			return db;
		});
		gs.setReceivePackFactory(new DefaultReceivePackFactory() {
			@Override
			public ReceivePack create(HttpServletRequest req, Repository db)
					throws ServiceNotEnabledException,
					ServiceNotAuthorizedException {
				ReceivePack recv = super.create(req, db);
				if (maxPackSize > 0)
					recv.setMaxPackSizeLimit(maxPackSize);
				if (postHook != null)
					recv.setPostReceiveHook(postHook);
				if (preHook != null)
					recv.setPreReceiveHook(preHook);
				if (oc != null)
					recv.setObjectChecker(oc);
				return recv;
			}

		});
		app.addServlet(new ServletHolder(gs), "/*");

		server.setUp();

		srvRepo = srv.getRepository();
		srvURI = toURIish(app, repoName);

		StoredConfig cfg = srvRepo.getConfig();
		cfg.setBoolean("http", null, "receivepack", true);
		cfg.save();
	}

	/**
	 * Configure a {@link GitServlet} that faces a {@link IllegalStateException}
	 * during executing preReceiveHooks. This used to lead to exceptions with a
	 * description of "invalid channel 101" on the client side. Make sure
	 * clients receive the correct response on the correct sideband.
	 *
	 * @throws Exception
	 */
	@Test
	public void testRuntimeExceptionInPreReceiveHook() throws Exception {
		final TestRepository client = createTestRepository();
		final RevBlob Q_txt = client
				.blob("some blob content to measure pack size");
		final RevCommit Q = client.commit().add("Q", Q_txt).create();
		final Repository clientRepo = client.getRepository();
		final String srvBranchName = Constants.R_HEADS + "new.branch";

		maxPackSize = 0;
		postHook = null;
		preHook = (ReceivePack rp, Collection<ReceiveCommand> commands) -> {
			throw new IllegalStateException();
		};

		try (Transport t = Transport.open(clientRepo, srvURI)) {
			RemoteRefUpdate update = new RemoteRefUpdate(clientRepo, Q.name(),
					srvBranchName, false, null, null);
			try {
				t.push(NullProgressMonitor.INSTANCE,
						Collections.singleton(update));
				fail("should not reach this line");
			} catch (Exception e) {
				assertTrue(e instanceof TransportException);
			}
		}
	}

	/**
	 * Configure a {@link GitServlet} that faces a {@link IllegalStateException}
	 * during executing objectChecking.
	 *
	 * @throws Exception
	 */
	@Test
	public void testObjectCheckerException() throws Exception {
		final TestRepository client = createTestRepository();
		final RevBlob Q_txt = client
				.blob("some blob content to measure pack size");
		final RevCommit Q = client.commit().add("Q", Q_txt).create();
		final Repository clientRepo = client.getRepository();
		final String srvBranchName = Constants.R_HEADS + "new.branch";

		maxPackSize = 0;
		postHook = null;
		preHook = null;
		oc = new ObjectChecker() {
			@Override
			public void checkCommit(AnyObjectId id, byte[] raw)
					throws CorruptObjectException {
				throw new CorruptObjectException("refusing all commits");
			}
		};

		try (Transport t = Transport.open(clientRepo, srvURI)) {
			RemoteRefUpdate update = new RemoteRefUpdate(clientRepo, Q.name(),
					srvBranchName, false, null, null);
			try {
				t.push(NullProgressMonitor.INSTANCE,
						Collections.singleton(update));
				fail("should not reach this line");
			} catch (Exception e) {
				assertTrue(e instanceof TransportException);
			}
		}
	}

	/**
	 * Configure a {@link GitServlet} that faces a {@link TooLargePackException}
	 * during persisting the pack and a {@link IllegalStateException} during
	 * executing postReceiveHooks. This used to lead to exceptions with a
	 * description of "invalid channel 101" on the client side. Make sure
	 * clients receive the correct response about the too large pack on the
	 * correct sideband.
	 *
	 * @throws Exception
	 */
	@Test
	public void testUnpackErrorWithSubsequentExceptionInPostReceiveHook()
			throws Exception {
		final TestRepository client = createTestRepository();
		final RevBlob Q_txt = client
				.blob("some blob content to measure pack size");
		final RevCommit Q = client.commit().add("Q", Q_txt).create();
		final Repository clientRepo = client.getRepository();
		final String srvBranchName = Constants.R_HEADS + "new.branch";

		// this maxPackSize leads to an unPackError
		maxPackSize = 100;
		// this PostReceiveHook when called after an unsuccesfull unpack will
		// lead to an IllegalStateException
		postHook = (ReceivePack rp, Collection<ReceiveCommand> commands) -> {
			// the maxPackSize setting caused that the packfile couldn't be
			// saved to disk. Calling getPackSize() now will lead to a
			// IllegalStateException.
			rp.getPackSize();
		};

		try (Transport t = Transport.open(clientRepo, srvURI)) {
			RemoteRefUpdate update = new RemoteRefUpdate(clientRepo, Q.name(),
					srvBranchName, false, null, null);
			try {
				t.push(NullProgressMonitor.INSTANCE,
						Collections.singleton(update));
				fail("should not reach this line");
			} catch (Exception e) {
				assertTrue(e instanceof TooLargePackException);
			}
		}
	}
}

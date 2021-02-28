/*
 * Copyright (C) 2010, 2017 Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.junit.http;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.security.AbstractLoginService;
import org.eclipse.jetty.security.Authenticator;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Password;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jgit.transport.URIish;

/**
 * Tiny web application server for unit testing.
 * <p>
 * Tests should start the server in their {@code setUp()} method and stop the
 * server in their {@code tearDown()} method. Only while started the server's
 * URL and/or port number can be obtained.
 */
public class AppServer {
	/** Realm name for the secure access areas. */
	public static final String realm = "Secure Area";

	/** Username for secured access areas. */
	public static final String username = "agitter";

	/** Password for {@link #username} in secured access areas. */
	public static final String password = "letmein";

	/** SSL keystore password; must have at least 6 characters. */
	private static final String keyPassword = "mykeys";

	/** Role for authentication. */
	private static final String authRole = "can-access";

	static {
		// Install a logger that throws warning messages.
		//
		final String prop = "org.eclipse.jetty.util.log.class";
		System.setProperty(prop, RecordingLogger.class.getName());
	}

	private final Server server;

	private final HttpConfiguration config;

	private final ServerConnector connector;

	private final HttpConfiguration secureConfig;

	private final ServerConnector secureConnector;

	private final ContextHandlerCollection contexts;

	private final TestRequestLog log;

	private List<File> filesToDelete = new ArrayList<>();

	/**
	 * Constructor for <code>AppServer</code>.
	 */
	public AppServer() {
		this(0, -1);
	}

	/**
	 * Constructor for <code>AppServer</code>.
	 *
	 * @param port
	 *            the http port number; may be zero to allocate a port
	 *            dynamically
	 * @since 4.2
	 */
	public AppServer(int port) {
		this(port, -1);
	}

	/**
	 * Constructor for <code>AppServer</code>.
	 *
	 * @param port
	 *            for http, may be zero to allocate a port dynamically
	 * @param sslPort
	 *            for https,may be zero to allocate a port dynamically. If
	 *            negative, the server will be set up without https support.
	 * @since 4.9
	 */
	public AppServer(int port, int sslPort) {
		server = new Server();

		config = new HttpConfiguration();
		config.setSecureScheme("https");
		config.setSecurePort(0);
		config.setOutputBufferSize(32768);

		connector = new ServerConnector(server,
				new HttpConnectionFactory(config));
		connector.setPort(port);
		String ip;
		String hostName;
		try {
			final InetAddress me = InetAddress.getByName("localhost");
			ip = me.getHostAddress();
			connector.setHost(ip);
			hostName = InetAddress.getLocalHost().getCanonicalHostName();
		} catch (UnknownHostException e) {
			throw new RuntimeException("Cannot find localhost", e);
		}

		if (sslPort >= 0) {
			SslContextFactory sslContextFactory = createTestSslContextFactory(
					hostName);
			secureConfig = new HttpConfiguration(config);
			secureConnector = new ServerConnector(server,
					new SslConnectionFactory(sslContextFactory,
							HttpVersion.HTTP_1_1.asString()),
					new HttpConnectionFactory(secureConfig));
			secureConnector.setPort(sslPort);
			secureConnector.setHost(ip);
		} else {
			secureConfig = null;
			secureConnector = null;
		}

		contexts = new ContextHandlerCollection();

		log = new TestRequestLog();
		log.setHandler(contexts);

		if (secureConnector == null) {
			server.setConnectors(new Connector[] { connector });
		} else {
			server.setConnectors(
					new Connector[] { connector, secureConnector });
		}
		server.setHandler(log);
	}

	private SslContextFactory createTestSslContextFactory(String hostName) {
		SslContextFactory.Client factory = new SslContextFactory.Client(true);

		String dName = "CN=,OU=,O=,ST=,L=,C=";

		try {
			File tmpDir = Files.createTempDirectory("jks").toFile();
			tmpDir.deleteOnExit();
			makePrivate(tmpDir);
			File keyStore = new File(tmpDir, "keystore.jks");
			Runtime.getRuntime().exec(
					new String[] {
							"keytool", //
							"-keystore", keyStore.getAbsolutePath(), //
							"-storepass", keyPassword,
							"-alias", hostName, //
							"-genkeypair", //
							"-keyalg", "RSA", //
							"-keypass", keyPassword, //
							"-dname", dName, //
							"-validity", "2" //
					}).waitFor();
			keyStore.deleteOnExit();
			makePrivate(keyStore);
			filesToDelete.add(keyStore);
			filesToDelete.add(tmpDir);
			factory.setKeyStorePath(keyStore.getAbsolutePath());
			factory.setKeyStorePassword(keyPassword);
			factory.setKeyManagerPassword(keyPassword);
			factory.setTrustStorePath(keyStore.getAbsolutePath());
			factory.setTrustStorePassword(keyPassword);
		} catch (InterruptedException | IOException e) {
			throw new RuntimeException("Cannot create ssl key/certificate", e);
		}
		return factory;
	}

	private void makePrivate(File file) {
		file.setReadable(false);
		file.setWritable(false);
		file.setExecutable(false);
		file.setReadable(true, true);
		file.setWritable(true, true);
		if (file.isDirectory()) {
			file.setExecutable(true, true);
		}
	}

	/**
	 * Create a new servlet context within the server.
	 * <p>
	 * This method should be invoked before the server is started, once for each
	 * context the caller wants to register.
	 *
	 * @param path
	 *            path of the context; use "/" for the root context if binding
	 *            to the root is desired.
	 * @return the context to add servlets into.
	 */
	public ServletContextHandler addContext(String path) {
		assertNotYetSetUp();
		if ("".equals(path))
			path = "/";

		ServletContextHandler ctx = new ServletContextHandler();
		ctx.setContextPath(path);
		contexts.addHandler(ctx);

		return ctx;
	}

	/**
	 * Configure basic authentication.
	 *
	 * @param ctx
	 * @param methods
	 * @return servlet context handler
	 */
	public ServletContextHandler authBasic(ServletContextHandler ctx,
			String... methods) {
		assertNotYetSetUp();
		auth(ctx, new BasicAuthenticator(), methods);
		return ctx;
	}

	static class TestMappedLoginService extends AbstractLoginService {
		private String role;

		protected final Map<String, UserPrincipal> users = new ConcurrentHashMap<>();

		TestMappedLoginService(String role) {
			this.role = role;
		}

		@Override
		protected void doStart() throws Exception {
			UserPrincipal p = new UserPrincipal(username,
					new Password(password));
			users.put(username, p);
			super.doStart();
		}

		@Override
		protected String[] loadRoleInfo(UserPrincipal user) {
			if (users.get(user.getName()) == null) {
				return null;
			}
			return new String[] { role };
		}

		@Override
		protected UserPrincipal loadUserInfo(String user) {
			return users.get(user);
		}
	}

	private ConstraintMapping createConstraintMapping() {
		ConstraintMapping cm = new ConstraintMapping();
		cm.setConstraint(new Constraint());
		cm.getConstraint().setAuthenticate(true);
		cm.getConstraint().setDataConstraint(Constraint.DC_NONE);
		cm.getConstraint().setRoles(new String[] { authRole });
		cm.setPathSpec("/*");
		return cm;
	}

	private void auth(ServletContextHandler ctx, Authenticator authType,
			String... methods) {
		AbstractLoginService users = new TestMappedLoginService(authRole);
		List<ConstraintMapping> mappings = new ArrayList<>();
		if (methods == null || methods.length == 0) {
			mappings.add(createConstraintMapping());
		} else {
			for (String method : methods) {
				ConstraintMapping cm = createConstraintMapping();
				cm.setMethod(method.toUpperCase(Locale.ROOT));
				mappings.add(cm);
			}
		}

		ConstraintSecurityHandler sec = new ConstraintSecurityHandler();
		sec.setRealmName(realm);
		sec.setAuthenticator(authType);
		sec.setLoginService(users);
		sec.setConstraintMappings(
				mappings.toArray(new ConstraintMapping[0]));
		sec.setHandler(ctx);

		contexts.removeHandler(ctx);
		contexts.addHandler(sec);
	}

	/**
	 * Start the server on a random local port.
	 *
	 * @throws Exception
	 *             the server cannot be started, testing is not possible.
	 */
	public void setUp() throws Exception {
		RecordingLogger.clear();
		log.clear();
		server.start();
		config.setSecurePort(getSecurePort());
		if (secureConfig != null) {
			secureConfig.setSecurePort(getSecurePort());
		}
	}

	/**
	 * Shutdown the server.
	 *
	 * @throws Exception
	 *             the server refuses to halt, or wasn't running.
	 */
	public void tearDown() throws Exception {
		RecordingLogger.clear();
		log.clear();
		server.stop();
		for (File f : filesToDelete) {
			f.delete();
		}
		filesToDelete.clear();
	}

	/**
	 * Get the URI to reference this server.
	 * <p>
	 * The returned URI includes the proper host name and port number, but does
	 * not contain a path.
	 *
	 * @return URI to reference this server's root context.
	 */
	public URI getURI() {
		assertAlreadySetUp();
		String host = connector.getHost();
		if (host.contains(":") && !host.startsWith("["))
			host = "[" + host + "]";
		final String uri = "http://" + host + ":" + getPort();
		try {
			return new URI(uri);
		} catch (URISyntaxException e) {
			throw new RuntimeException("Unexpected URI error on " + uri, e);
		}
	}

	/**
	 * Get port.
	 *
	 * @return the local port number the server is listening on.
	 */
	public int getPort() {
		assertAlreadySetUp();
		return connector.getLocalPort();
	}

	/**
	 * Get secure port.
	 *
	 * @return the HTTPS port or -1 if not configured.
	 */
	public int getSecurePort() {
		assertAlreadySetUp();
		return secureConnector != null ? secureConnector.getLocalPort() : -1;
	}

	/**
	 * Get requests.
	 *
	 * @return all requests since the server was started.
	 */
	public List<AccessEvent> getRequests() {
		return new ArrayList<>(log.getEvents());
	}

	/**
	 * Get requests.
	 *
	 * @param base
	 *            base URI used to access the server.
	 * @param path
	 *            the path to locate requests for, relative to {@code base}.
	 * @return all requests which match the given path.
	 */
	public List<AccessEvent> getRequests(URIish base, String path) {
		return getRequests(HttpTestCase.join(base, path));
	}

	/**
	 * Get requests.
	 *
	 * @param path
	 *            the path to locate requests for.
	 * @return all requests which match the given path.
	 */
	public List<AccessEvent> getRequests(String path) {
		ArrayList<AccessEvent> r = new ArrayList<>();
		for (AccessEvent event : log.getEvents()) {
			if (event.getPath().equals(path)) {
				r.add(event);
			}
		}
		return r;
	}

	private void assertNotYetSetUp() {
		assertFalse("server is not running", server.isRunning());
	}

	private void assertAlreadySetUp() {
		assertTrue("server is running", server.isRunning());
	}
}

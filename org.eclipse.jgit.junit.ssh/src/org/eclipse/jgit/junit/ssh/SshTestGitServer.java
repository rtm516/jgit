/*
 * Copyright (C) 2018, 2020 Thomas Wolf <thomas.wolf@paranor.ch> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
package org.eclipse.jgit.junit.ssh;

import static org.apache.sshd.core.CoreModuleProperties.SERVER_EXTRA_IDENTIFICATION_LINES;
import static org.apache.sshd.core.CoreModuleProperties.SERVER_EXTRA_IDENT_LINES_SEPARATOR;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.PropertyResolver;
import org.apache.sshd.common.SshConstants;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.common.signature.BuiltinSignatures;
import org.apache.sshd.common.signature.Signature;
import org.apache.sshd.common.util.buffer.Buffer;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.common.util.threads.CloseableExecutorService;
import org.apache.sshd.common.util.threads.ThreadUtils;
import org.apache.sshd.server.ServerAuthenticationManager;
import org.apache.sshd.server.ServerBuilder;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.UserAuth;
import org.apache.sshd.server.auth.UserAuthFactory;
import org.apache.sshd.server.auth.gss.GSSAuthenticator;
import org.apache.sshd.server.auth.gss.UserAuthGSS;
import org.apache.sshd.server.auth.gss.UserAuthGSSFactory;
import org.apache.sshd.server.auth.keyboard.DefaultKeyboardInteractiveAuthenticator;
import org.apache.sshd.server.command.AbstractCommandSupport;
import org.apache.sshd.server.session.ServerSession;
import org.apache.sshd.server.shell.UnknownCommand;
import org.apache.sshd.server.subsystem.SubsystemFactory;
import org.apache.sshd.sftp.server.SftpSubsystemFactory;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.annotations.Nullable;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.UploadPack;

/**
 * A simple ssh/sftp git <em>test</em> server based on Apache MINA sshd.
 * <p>
 * Supports only a single repository. Authenticates only the given test user
 * against his given test public key. Supports fetch and push.
 * </p>
 *
 * @since 5.2
 */
public class SshTestGitServer {

	/**
	 * Simple echo test command. Replies with the command string as passed. If
	 * of the form "echo [int] anything", takes the integer value as a delay in
	 * seconds before replying, which may be useful to test various
	 * timeout-related things.
	 *
	 * @since 5.9
	 */
	public static final String ECHO_COMMAND = "echo";

	@NonNull
	protected final String testUser;

	@NonNull
	protected final Repository repository;

	@NonNull
	protected final List<KeyPair> hostKeys = new ArrayList<>();

	protected final SshServer server;

	@NonNull
	protected PublicKey testKey;

	private final CloseableExecutorService executorService = ThreadUtils
			.newFixedThreadPool("SshTestGitServerPool", 2);

	/**
	 * Creates a ssh git <em>test</em> server. It serves one single repository,
	 * and accepts public-key authentication for exactly one test user.
	 *
	 * @param testUser
	 *            user name of the test user
	 * @param testKey
	 *            public key file of the test user
	 * @param repository
	 *            to serve
	 * @param hostKey
	 *            the unencrypted private key to use as host key
	 * @throws IOException
	 * @throws GeneralSecurityException
	 */
	public SshTestGitServer(@NonNull String testUser, @NonNull Path testKey,
			@NonNull Repository repository, @NonNull byte[] hostKey)
			throws IOException, GeneralSecurityException {
		this(testUser, readPublicKey(testKey), repository,
				readKeyPair(hostKey));
	}

	/**
	 * Creates a ssh git <em>test</em> server. It serves one single repository,
	 * and accepts public-key authentication for exactly one test user.
	 *
	 * @param testUser
	 *            user name of the test user
	 * @param testKey
	 *            public key file of the test user
	 * @param repository
	 *            to serve
	 * @param hostKey
	 *            the unencrypted private key to use as host key
	 * @throws IOException
	 * @throws GeneralSecurityException
	 * @since 5.9
	 */
	public SshTestGitServer(@NonNull String testUser, @NonNull Path testKey,
			@NonNull Repository repository, @NonNull KeyPair hostKey)
			throws IOException, GeneralSecurityException {
		this(testUser, readPublicKey(testKey), repository, hostKey);
	}

	/**
	 * Creates a ssh git <em>test</em> server. It serves one single repository,
	 * and accepts public-key authentication for exactly one test user.
	 *
	 * @param testUser
	 *            user name of the test user
	 * @param testKey
	 *            the {@link PublicKey} of the test user
	 * @param repository
	 *            to serve
	 * @param hostKey
	 *            the {@link KeyPair} to use as host key
	 * @since 5.9
	 */
	public SshTestGitServer(@NonNull String testUser,
			@NonNull PublicKey testKey, @NonNull Repository repository,
			@NonNull KeyPair hostKey) {
		this.testUser = testUser;
		setTestUserPublicKey(testKey);
		this.repository = repository;
		ServerBuilder builder = ServerBuilder.builder()
				.signatureFactories(getSignatureFactories());
		server = builder.build();
		hostKeys.add(hostKey);
		server.setKeyPairProvider((session) -> hostKeys);

		configureAuthentication();

		List<SubsystemFactory> subsystems = configureSubsystems();
		if (!subsystems.isEmpty()) {
			server.setSubsystemFactories(subsystems);
		}

		configureShell();

		server.setCommandFactory((channel, command) -> {
			if (command.startsWith(RemoteConfig.DEFAULT_UPLOAD_PACK)) {
				return new GitUploadPackCommand(command, executorService);
			} else if (command.startsWith(RemoteConfig.DEFAULT_RECEIVE_PACK)) {
				return new GitReceivePackCommand(command, executorService);
			} else if (command.startsWith(ECHO_COMMAND)) {
				return new EchoCommand(command, executorService);
			}
			return new UnknownCommand(command);
		});
	}

	/**
	 * Apache MINA sshd 2.6.0 has removed DSA, DSA_CERT and RSA_CERT. We have to
	 * set it up explicitly to still allow users to connect with DSA keys.
	 *
	 * @return a list of supported signature factories
	 */
	@SuppressWarnings("deprecation")
	private static List<NamedFactory<Signature>> getSignatureFactories() {
		// @formatter:off
		return Arrays.asList(
                BuiltinSignatures.nistp256_cert,
                BuiltinSignatures.nistp384_cert,
                BuiltinSignatures.nistp521_cert,
                BuiltinSignatures.ed25519_cert,
                BuiltinSignatures.rsaSHA512_cert,
                BuiltinSignatures.rsaSHA256_cert,
                BuiltinSignatures.rsa_cert,
                BuiltinSignatures.nistp256,
                BuiltinSignatures.nistp384,
                BuiltinSignatures.nistp521,
                BuiltinSignatures.ed25519,
                BuiltinSignatures.sk_ecdsa_sha2_nistp256,
                BuiltinSignatures.sk_ssh_ed25519,
                BuiltinSignatures.rsaSHA512,
                BuiltinSignatures.rsaSHA256,
                BuiltinSignatures.rsa,
                BuiltinSignatures.dsa_cert,
                BuiltinSignatures.dsa);
		// @formatter:on
	}

	private static PublicKey readPublicKey(Path key)
			throws IOException, GeneralSecurityException {
		return AuthorizedKeyEntry.readAuthorizedKeys(key).get(0)
				.resolvePublicKey(null, PublicKeyEntryResolver.IGNORING);
	}

	private static KeyPair readKeyPair(byte[] keyMaterial)
			throws IOException, GeneralSecurityException {
		try (ByteArrayInputStream in = new ByteArrayInputStream(keyMaterial)) {
			return SecurityUtils.loadKeyPairIdentities(null, null, in, null)
					.iterator().next();
		}
	}

	private static class FakeUserAuthGSS extends UserAuthGSS {
		@Override
		protected @Nullable Boolean doAuth(Buffer buffer, boolean initial)
				throws Exception {
			// We always reply that we did do this, but then we fail at the
			// first token message. That way we can test that the client-side
			// sends the correct initial request and then is skipped correctly,
			// even if it causes a GSSException if Kerberos isn't configured at
			// all.
			if (initial) {
				ServerSession session = getServerSession();
				Buffer b = session.createBuffer(
						SshConstants.SSH_MSG_USERAUTH_INFO_REQUEST);
				b.putBytes(KRB5_MECH.getDER());
				session.writePacket(b);
				return null;
			}
			return Boolean.FALSE;
		}
	}

	private List<UserAuthFactory> getAuthFactories() {
		List<UserAuthFactory> authentications = new ArrayList<>();
		authentications.add(new UserAuthGSSFactory() {
			@Override
			public UserAuth createUserAuth(ServerSession session)
					throws IOException {
				return new FakeUserAuthGSS();
			}
		});
		authentications.add(
				ServerAuthenticationManager.DEFAULT_USER_AUTH_PUBLIC_KEY_FACTORY);
		authentications.add(
				ServerAuthenticationManager.DEFAULT_USER_AUTH_KB_INTERACTIVE_FACTORY);
		authentications.add(
				ServerAuthenticationManager.DEFAULT_USER_AUTH_PASSWORD_FACTORY);
		return authentications;
	}

	/**
	 * Configures the authentication mechanisms of this test server. Invoked
	 * from the constructor. The default sets up public key authentication for
	 * the test user, and a gssapi-with-mic authenticator that pretends to
	 * support this mechanism, but that then refuses to authenticate anyone.
	 */
	protected void configureAuthentication() {
		server.setUserAuthFactories(getAuthFactories());
		// Disable some authentications
		server.setPasswordAuthenticator(null);
		server.setKeyboardInteractiveAuthenticator(null);
		server.setHostBasedAuthenticator(null);
		// Pretend we did gssapi-with-mic.
		server.setGSSAuthenticator(new GSSAuthenticator() {
			@Override
			public boolean validateInitialUser(ServerSession session,
					String user) {
				return false;
			}
		});
		// Accept only the test user/public key
		server.setPublickeyAuthenticator((userName, publicKey, session) -> {
			return SshTestGitServer.this.testUser.equals(userName) && KeyUtils
					.compareKeys(SshTestGitServer.this.testKey, publicKey);
		});
	}

	/**
	 * Configures the test server's subsystems (sftp, scp). Invoked from the
	 * constructor. The default provides a simple SFTP setup with the root
	 * directory as the given repository's .git directory's parent. (I.e., at
	 * the directory containing the .git directory.)
	 *
	 * @return A possibly empty collection of subsystems.
	 */
	@NonNull
	protected List<SubsystemFactory> configureSubsystems() {
		// SFTP.
		server.setFileSystemFactory(new VirtualFileSystemFactory(repository
				.getDirectory().getParentFile().getAbsoluteFile().toPath()));
		return Collections
				.singletonList((new SftpSubsystemFactory.Builder()).build());
	}

	/**
	 * Configures shell access for the test server. The default provides no
	 * shell at all.
	 */
	protected void configureShell() {
		// No shell
		server.setShellFactory(null);
	}

	/**
	 * Adds an additional host key to the server.
	 *
	 * @param key
	 *            path to the private key file; should not be encrypted
	 * @param inFront
	 *            whether to add the new key before other existing keys
	 * @throws IOException
	 *             if the file denoted by the {@link Path} {@code key} cannot be
	 *             read
	 * @throws GeneralSecurityException
	 *             if the key contained in the file cannot be read
	 */
	public void addHostKey(@NonNull Path key, boolean inFront)
			throws IOException, GeneralSecurityException {
		try (InputStream in = Files.newInputStream(key)) {
			KeyPair pair = SecurityUtils
					.loadKeyPairIdentities(null,
							NamedResource.ofName(key.toString()), in, null)
					.iterator().next();
			addHostKey(pair, inFront);
		}
	}

	/**
	 * Adds an additional host key to the server.
	 *
	 * @param key
	 *            {@link KeyPair} to add
	 * @param inFront
	 *            whether to add the new key before other existing keys
	 * @since 5.8
	 */
	public void addHostKey(@NonNull KeyPair key, boolean inFront) {
		if (inFront) {
			hostKeys.add(0, key);
		} else {
			hostKeys.add(key);
		}
	}

	/**
	 * Enable password authentication. The server will accept the test user's
	 * name, converted to all upper-case, as password.
	 */
	public void enablePasswordAuthentication() {
		server.setPasswordAuthenticator((user, pwd, session) -> {
			return testUser.equals(user)
					&& testUser.toUpperCase(Locale.ROOT).equals(pwd);
		});
	}

	/**
	 * Enable keyboard-interactive authentication. The server will accept the
	 * test user's name, converted to all upper-case, as password.
	 */
	public void enableKeyboardInteractiveAuthentication() {
		server.setPasswordAuthenticator((user, pwd, session) -> {
			return testUser.equals(user)
					&& testUser.toUpperCase(Locale.ROOT).equals(pwd);
		});
		server.setKeyboardInteractiveAuthenticator(
				DefaultKeyboardInteractiveAuthenticator.INSTANCE);
	}

	/**
	 * Retrieves the server's {@link PropertyResolver}, giving access to server
	 * properties.
	 *
	 * @return the {@link PropertyResolver}
	 * @since 5.9
	 */
	public PropertyResolver getPropertyResolver() {
		return server;
	}

	/**
	 * Starts the test server, listening on a random port.
	 *
	 * @return the port the server listens on; test clients should connect to
	 *         that port
	 * @throws IOException
	 */
	public int start() throws IOException {
		server.start();
		return server.getPort();
	}

	/**
	 * Stops the test server.
	 *
	 * @throws IOException
	 */
	public void stop() throws IOException {
		executorService.shutdownNow();
		server.stop(true);
	}

	/**
	 * Sets the test user's public key on the server.
	 *
	 * @param key
	 *            to set
	 * @throws IOException
	 *             if the file cannot be read
	 * @throws GeneralSecurityException
	 *             if the public key cannot be extracted from the file
	 */
	public void setTestUserPublicKey(Path key)
			throws IOException, GeneralSecurityException {
		this.testKey = readPublicKey(key);
	}

	/**
	 * Sets the test user's public key on the server.
	 *
	 * @param key
	 *            to set
	 *
	 * @since 5.8
	 */
	public void setTestUserPublicKey(@NonNull PublicKey key) {
		this.testKey = key;
	}

	/**
	 * Sets the lines the server sends before its server identification in the
	 * initial protocol version exchange.
	 *
	 * @param lines
	 *            to send
	 * @since 5.5
	 */
	public void setPreamble(String... lines) {
		if (lines != null && lines.length > 0) {
			SERVER_EXTRA_IDENTIFICATION_LINES.set(server, String.join(
					String.valueOf(SERVER_EXTRA_IDENT_LINES_SEPARATOR), lines));
		}
	}

	private class GitUploadPackCommand extends AbstractCommandSupport {

		protected GitUploadPackCommand(String command,
				CloseableExecutorService executorService) {
			super(command, ThreadUtils.noClose(executorService));
		}

		@Override
		public void run() {
			UploadPack uploadPack = new UploadPack(repository);
			String gitProtocol = getEnvironment().getEnv().get("GIT_PROTOCOL");
			if (gitProtocol != null) {
				uploadPack
						.setExtraParameters(Collections.singleton(gitProtocol));
			}
			try {
				uploadPack.upload(getInputStream(), getOutputStream(),
						getErrorStream());
				onExit(0);
			} catch (IOException e) {
				log.warn(
						MessageFormat.format("Could not run {0}", getCommand()),
						e);
				onExit(-1, e.toString());
			}
		}

	}

	private class GitReceivePackCommand extends AbstractCommandSupport {

		protected GitReceivePackCommand(String command,
				CloseableExecutorService executorService) {
			super(command, ThreadUtils.noClose(executorService));
		}

		@Override
		public void run() {
			try {
				new ReceivePack(repository).receive(getInputStream(),
						getOutputStream(), getErrorStream());
				onExit(0);
			} catch (IOException e) {
				log.warn(
						MessageFormat.format("Could not run {0}", getCommand()),
						e);
				onExit(-1, e.toString());
			}
		}

	}

	/**
	 * Simple echo command that echoes back the command string. If the first
	 * argument is a positive integer, it's taken as a delay (in seconds) before
	 * replying. Assumes UTF-8 character encoding.
	 */
	private static class EchoCommand extends AbstractCommandSupport {

		protected EchoCommand(String command,
				CloseableExecutorService executorService) {
			super(command, ThreadUtils.noClose(executorService));
		}

		@Override
		public void run() {
			String[] parts = getCommand().split(" ");
			int timeout = 0;
			if (parts.length >= 2) {
				try {
					timeout = Integer.parseInt(parts[1]);
				} catch (NumberFormatException e) {
					// No timeout.
				}
				if (timeout > 0) {
					try {
						Thread.sleep(TimeUnit.SECONDS.toMillis(timeout));
					} catch (InterruptedException e) {
						// Ignore.
					}
				}
			}
			try {
				doEcho(getCommand(), getOutputStream());
				onExit(0);
			} catch (IOException e) {
				log.warn(
						MessageFormat.format("Could not run {0}", getCommand()),
						e);
				onExit(-1, e.toString());
			}
		}

		private void doEcho(String text, OutputStream stream)
				throws IOException {
			stream.write(text.getBytes(StandardCharsets.UTF_8));
			stream.flush();
		}
	}
}

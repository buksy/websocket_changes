package org.java_websocket;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.UnresolvedAddressException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_10;
import org.java_websocket.exceptions.InvalidHandshakeException;
import org.java_websocket.handshake.HandshakeImpl1Client;
import org.java_websocket.handshake.Handshakedata;
import org.java_websocket.handshake.ServerHandshake;

import sun.misc.BASE64Decoder;

import com.extl.vnc.socket.SocketClient;

/**
 * The <tt>WebSocketClient</tt> is an abstract class that expects a valid
 * "ws://" URI to connect to. When connected, an instance recieves important
 * events related to the life of the connection. A subclass must implement
 * <var>onOpen</var>, <var>onClose</var>, and <var>onMessage</var> to be useful.
 * An instance can send messages to it's connected server via the
 * <var>send</var> method.
 * 
 * @author Nathan Rajlich
 */
public abstract class WebSocketClient extends WebSocketAdapter implements
		Runnable {

	private static final String KEYSTORE_PASS = "temppass";
	
	
	/**
	 * The URI this client is supposed to connect to.
	 */
	private URI uri = null;
	/**
	 * The WebSocket instance this client object wraps.
	 */
	private WebSocket conn = null;
	/**
	 * The SocketChannel instance this client uses.
	 */
	private SocketClient client = null;
	/**
	 * The 'Selector' used to get event keys from the underlying socket.
	 */
	// private Selector selector = null;

	private Thread thread;

	private Draft draft;

	private final Lock closelock = new ReentrantLock();

	private Map<String, String> headers;

	public WebSocketClient(URI serverURI) {
		this(serverURI, new Draft_10());
	}

	/**
	 * Constructs a WebSocketClient instance and sets it to the connect to the
	 * specified URI. The client does not attampt to connect automatically. You
	 * must call <var>connect</var> first to initiate the socket connection.
	 */
	public WebSocketClient(URI serverUri, Draft draft) {
		this(serverUri, draft, null);
	}

	public WebSocketClient(URI serverUri, Draft draft,
			Map<String, String> headers) {
		if (serverUri == null) {
			throw new IllegalArgumentException();
		}
		if (draft == null) {
			throw new IllegalArgumentException(
					"null as draft is permitted for `WebSocketServer` only!");
		}
		this.uri = serverUri;
		this.draft = draft;
		this.headers = headers;
	}

	/**
	 * Gets the URI that this WebSocketClient is connected to.
	 * 
	 * @return The <tt>URI</tt> for this WebSocketClient.
	 */
	public URI getURI() {
		return uri;
	}

	/** Returns the protocol version this client uses. */
	public Draft getDraft() {
		return draft;
	}

	/**
	 * Starts a background thread that attempts and maintains a WebSocket
	 * connection to the URI specified in the constructor or via
	 * <var>setURI</var>. <var>setURI</var>.
	 */
	public void connect() {
		if (thread != null)
			throw new IllegalStateException("already/still connected");
		thread = new Thread(this);
		thread.start();
	}

	public void close() {
		if (thread != null) {
			thread.interrupt();
			closelock.lock();
			try {
				// if( selector != null )
				// selector.wakeup();
			} finally {
				closelock.unlock();
			}
		}

	}

	/**
	 * Sends <var>text</var> to the connected WebSocket server.
	 * 
	 * @param text
	 *            The String to send to the WebSocket server.
	 * @throws IOException
	 */
	public void send(String text) throws NotYetConnectedException, IOException {
		if (conn != null) {
			conn.send(text);
		}
	}

	/**
	 * Sends <var>data</var> to the connected WebSocket server.
	 * 
	 * @param data
	 *            The Byte-Array of data to send to the WebSocket server.
	 * @throws IOException
	 * @throws IllegalArgumentException
	 * @throws InterruptedException 
	 */
	public void send(byte[] data) throws NotYetConnectedException,
			IllegalArgumentException, IOException, InterruptedException {
		if (conn != null) {
			conn.send(data);
		}
	}

	private void tryToConnect(InetSocketAddress remote) throws IOException,
			InvalidHandshakeException, InterruptedException {
		client = new SocketClient(remote);
		String certdata = System.getProperty("SSLCERT");
		//System.out.println(new Date()+ " Stating cert Handling");
		if (certdata != null) {
			BASE64Decoder base64 = new BASE64Decoder();
			ByteArrayInputStream bio = new ByteArrayInputStream(
					(base64.decodeBuffer(certdata)));
			
			try {
				// Setting up the ssl
				 CertificateFactory cf = CertificateFactory.getInstance("X.509");
				 X509Certificate cert = (X509Certificate)cf.generateCertificate(bio);
				 
				 //System.out.println(cert.toString());
				 KeyStore ks = createkeyStore();
				 ks.setCertificateEntry("tmp-cret", cert);
				 ks.load(null,KEYSTORE_PASS.toCharArray());
				 SSLContext sslContext = SSLContext.getInstance("TLS");
				 //System.out.println(ks.getCertificate("10.10.0.2").toString());
				 
				 KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
				 kmf.init(ks, KEYSTORE_PASS.toCharArray());
		         TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
		         tmf.init(ks);
				 
		         sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
		         client.setSSLContex(sslContext);
		         System.out.println(new Date()+ " stopping cert handling");
			} catch (UnrecoverableKeyException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (KeyManagementException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (CertificateException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (KeyStoreException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (NoSuchAlgorithmException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		//System.out.println(new Date()+ " Issuning connection ");
		client.connect();
		//System.out.println(new Date()+ " connection issued ");
		while (!client.isConnected())
			;
		//System.out.println(new Date()+ " connection done ");
		conn = new WebSocketImpl(this, draft, client);
		sendHandshake();
		//System.out.println(new Date()+ " Handshake send ");
	}

	// Runnable IMPLEMENTATION /////////////////////////////////////////////////
	public void run() {
		if (thread == null)
			thread = Thread.currentThread();
		interruptableRun();
		thread = null;
	}

	protected final void interruptableRun() {
		try {
			tryToConnect(new InetSocketAddress(uri.getHost(), getPort()));
			// finishConnect();
			ByteBuffer buff = ByteBuffer.allocate(256);
			while (true) {
				if(	conn.read(buff)){
					conn.decode(buff);
				}
			}
		} catch (ClosedByInterruptException e) {
			onWebsocketError(null, e);
			return;
		} catch (IOException e) {//
			onWebsocketError(conn, e);
			return;
		} catch (SecurityException e) {
			onWebsocketError(conn, e);
			return;
		} catch (UnresolvedAddressException e) {
			onWebsocketError(conn, e);
			return;
		}
		catch (InvalidHandshakeException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private int getPort() {
		int port = uri.getPort();
		return port == -1 ? WebSocket.DEFAULT_PORT : port;
	}

	
	private void sendHandshake() throws IOException, InvalidHandshakeException,
			InterruptedException {
		String path;
		String part1 = uri.getPath();
		String part2 = uri.getQuery();
		if (part1 == null || part1.length() == 0)
			path = "/";
		else
			path = part1;
		if (part2 != null)
			path += "?" + part2;
		int port = getPort();
		String host = uri.getHost()
				+ (port != WebSocket.DEFAULT_PORT ? ":" + port : "");

		HandshakeImpl1Client handshake = new HandshakeImpl1Client();
		handshake.setResourceDescriptor(path);
		handshake.put("Host", host);
		if (headers != null) {
			for (Map.Entry<String, String> kv : headers.entrySet()) {
				handshake.put(kv.getKey(), kv.getValue());
			}
		}
		conn.startHandshake(handshake);
	}

	/**
	 * Retrieve the WebSocket 'readyState'. This represents the state of the
	 * connection. It returns a numerical value, as per W3C WebSockets specs.
	 * 
	 * @return Returns '0 = CONNECTING', '1 = OPEN', '2 = CLOSING' or '3 =
	 *         CLOSED'
	 */
	public int getReadyState() {
		return conn.getReadyState();
	}

	/**
	 * Amount of data buffered/queued but not sent yet.
	 * 
	 * In details, it returns the number of bytes of application data (UTF-8
	 * text and binary data) that have been queued using send() but that, as of
	 * the last time the event loop started executing a task, had not yet been
	 * transmitted to the network.
	 * 
	 * @return Amount still buffered/queued but not sent yet.
	 */
	public long getBufferedAmount() {
		return conn.bufferedDataAmount();
	}

	/**
	 * Calls subclass' implementation of <var>onMessage</var>.
	 * 
	 * @param conn
	 * @param message
	 */
	@Override
	public final void onWebsocketMessage(WebSocket conn, String message) {
		onMessage(message);
	}

	@Override
	public final void onWebsocketMessage(WebSocket conn, ByteBuffer blob) {
		onMessage(blob);
	}

	/**
	 * Calls subclass' implementation of <var>onOpen</var>.
	 * 
	 * @param conn
	 */
	@Override
	public final void onWebsocketOpen(WebSocket conn, Handshakedata handshake) {
		onOpen((ServerHandshake) handshake);
	}

	/**
	 * Calls subclass' implementation of <var>onClose</var>.
	 * 
	 * @param conn
	 */
	@Override
	public final void onWebsocketClose(WebSocket conn, int code, String reason,
			boolean remote) {
		thread.interrupt();
		onClose(code, reason, remote);
	}

	/**
	 * Calls subclass' implementation of <var>onIOError</var>.
	 * 
	 * @param conn
	 */
	@Override
	public final void onWebsocketError(WebSocket conn, Exception ex) {
		onError(ex);
	}

	@Override
	public final void onWriteDemand(WebSocket conn) {
		// if(selector != null) {
		// try {
		// if (client !=null && client.isOpen())
		// client.register(selector, SelectionKey.OP_READ |
		// SelectionKey.OP_WRITE);
		// } catch (ClosedChannelException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }
		// selector.wakeup();
		// }else {
		// onError(new Exception("Selector seems to be colsed or nullyfied"));
		// }
	}

	public WebSocket getConnection() {
		return conn;
	}

	// ABTRACT METHODS /////////////////////////////////////////////////////////
	public abstract void onOpen(ServerHandshake handshakedata);

	public abstract void onMessage(String message);

	public abstract void onClose(int code, String reason, boolean remote);

	public abstract void onError(Exception ex);

	public void onMessage(ByteBuffer bytes) {
	};
	
	
	// Private methods 
	// All the private methods.. 
	private KeyStore createkeyStore() throws KeyStoreException, NoSuchAlgorithmException, CertificateException, IOException {
		KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
		ks.load(null, KEYSTORE_PASS.toCharArray());
		return ks;
	}
}

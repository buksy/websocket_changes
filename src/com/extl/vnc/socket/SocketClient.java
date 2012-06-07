package com.extl.vnc.socket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;


public class SocketClient extends Thread{
	
	
	private Selector selector = null;
	private SocketChannel client = null;
	private InetSocketAddress address = null;

	private SSLContext sslContext; 
	private SSLEngine sslEngine = null;
	private SSLHandler sslHandler = null;
	
	private boolean initConnDone = false;
	
	//private SocketInputStream socketInputStream = null ;
	private ByteBufferInputStream socketInputStream;
	private SocketOutputStream socketOutputStream = null; 
	
	public SocketClient(InetSocketAddress address) throws IOException {
		selector = Selector.open();
		this.address = address;
		//socketInputStream = new SocketInputStream();
		socketInputStream = new ByteBufferInputStream();
		socketOutputStream = new SocketOutputStream(this);
	}
	
	
	public void setSSLContex( SSLContext context) {
		this.sslContext = context;
	}
	

	
	public void connect() throws IOException {
		client = SocketChannel.open();
		client.configureBlocking( false );
		client.connect( address );
		client.register(selector, SelectionKey.OP_CONNECT);
		start();
	}

	
	public void run() {
		try {
			while (true) {
				int nsel = selector.select();
				if (nsel == 0)
					continue;
				Set<SelectionKey> selectedKeys = selector.selectedKeys();
				Iterator<SelectionKey>	it = selectedKeys.iterator();
				while (it.hasNext())
				{
					SelectionKey	key = it.next();
					it.remove();
					if (!key.isValid()) 
						continue;
					if(key.isConnectable()) {
						if(client.finishConnect()) {
							if(sslContext!=null) { 
								// Do the SSL handshake stuff ;
								sslEngine = sslContext.createSSLEngine(client.socket().getInetAddress().getHostName(), client.socket().getPort());
								sslEngine.setUseClientMode(true);
								sslHandler = new SSLHandler(sslEngine, client,selector);
							}
							client.register(selector,SelectionKey.OP_READ);
							initConnDone = true;
						}
					}
					if(key.isReadable()) {
						doRead();
					}
					if(key.isWritable()) {
						doWrite();
						if(sslHandler==null || sslHandler.isHandshakeDone())
							client.register(selector, SelectionKey.OP_READ);
					}
				}
			}
		
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}finally {
			try {
				selector.close();
				client.close();
				socketInputStream.close();
				socketOutputStream.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
	}
	
	private int doRead() throws IOException {
		if(sslHandler!=null) {
			ByteBuffer buf = sslHandler.getReadData();
			if(buf!=null) {
//				byte b[] = buf.array();
//				socketInputStream.setToRead(b);
				socketInputStream.addToReadQueue(buf);
				return buf.remaining();
			}
			
		}else {
			// Write the non SSL code
			
			
		}
		return 0;
	}
	
	private void doWrite() throws IOException{
		if(sslHandler!=null) {
		sslHandler.doTransfer(socketOutputStream.getByteBuffer());
		}else {
			//Write the non SSL bit of the transfer
		}
	}
	
	
	public boolean isConnected() {
		return (initConnDone && client!=null && client.isConnected()) ;
	}
	
	public OutputStream getOutputStream(){
		return socketOutputStream;
	}
	
	public InputStream getInputStream() {
		return socketInputStream;
	}
	
	public void close() throws IOException{
		if(sslHandler != null ) {
			sslHandler.stop();
		}
		client.close();
		selector.close();
	}
	
	
	protected void sendNow() throws IOException {
		if (client != null && client.isOpen()) {
			try {
				client.register(selector, SelectionKey.OP_WRITE | SelectionKey.OP_READ);
				selector.wakeup();
			} catch (ClosedChannelException e) {
				// TODO Auto-generated catch block
				throw new IOException ("Connection Closed ");
			}
		}
	}
	
	public InetSocketAddress getRemoteAddress() {
		return address;
	}
	
	public InetSocketAddress getLocalAddress() {
		return (InetSocketAddress)(client.socket().getLocalSocketAddress());
	}
	

}

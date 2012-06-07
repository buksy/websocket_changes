package com.extl.vnc.socket;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.Status;
import javax.net.ssl.SSLException;

public class SSLHandler {

	private SSLEngine engine ; 
	private SocketChannel channel; 
	private Selector selector ; 
	
	private boolean isHanshakeDone = false;
	
	private ByteBuffer	appSendBuffer = null;
	private ByteBuffer	netSendBuffer = null;
	private ByteBuffer	appRecvBuffer = null;
	private ByteBuffer	netRecvBuffer = null;
	
	public SSLHandler(SSLEngine engine , SocketChannel channle, Selector selector) throws SSLException {
		this.engine = engine; 
		this.channel = channle;
		this.selector = selector;
		this.appSendBuffer = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
		this.netSendBuffer = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
		this.appRecvBuffer = ByteBuffer.allocate(engine.getSession().getApplicationBufferSize());
		this.netRecvBuffer = ByteBuffer.allocate(engine.getSession().getPacketBufferSize());
		engine.beginHandshake();
	}
	
	
	public ByteBuffer getReadData() throws IOException{
		//System.out.println(appRecvBuffer.limit());
		int x = appRecvBuffer.position();
		if(x > 0) {
			//System.out.println("App buffer has data");
			
			appRecvBuffer.flip();
			byte b[] = new byte[x];
			appRecvBuffer.get(b);
			appRecvBuffer.compact();
			return ByteBuffer.wrap(b);
		}
		int out = read();
		//System.out.println("Read from handler "+out + " -- "+Thread.currentThread().getId());
		if(out > 0) {
			appRecvBuffer.flip();
			byte b[] = new byte[out];
			appRecvBuffer.get(b);
			appRecvBuffer.compact();
			return ByteBuffer.wrap(b);
		}
		return null;
	}
	
	
	private int read() throws IOException{
//		System.out.println("Doing a read ");
		int count = 0;
		while ((count = channel.read(netRecvBuffer)) >0);
		if(count==-1)
			throw new IOException("Connection closed");
		
		
		if(!isHanshakeDone) {
			if(!handleSSLHandshake2())
				return 0;
			else {
				if(appSendBuffer.hasRemaining()) {
					channel.register(selector, SelectionKey.OP_WRITE);
					selector.wakeup();
					return 0;
				}
			}
		}
		
		int retval = 0;
		boolean loop = true;
		while(loop) {
			netRecvBuffer.flip();
			SSLEngineResult engineResult = engine.unwrap(netRecvBuffer,appRecvBuffer);
			netRecvBuffer.compact();
			 
			switch (engineResult.getStatus()) {
				case BUFFER_UNDERFLOW: // There is not enough data read from the network buffer to do the handshaking
					// Setting the selector to be read again and pass 0 to the caller saying there is nothing to read
					//System.out.println("Read buffer underflow");
					channel.register(selector, SelectionKey.OP_READ);
					selector.wakeup();
					loop = false;
					break;
				case BUFFER_OVERFLOW : // The appbuffer is full the caller really needs to read the current buffer. 
					// Please not this not a problem in the our current implementation as we make sure we are writing the data to the stream
					//System.out.println("Read buffer overflow");
					retval = appRecvBuffer.position();
					loop = false;
					break;
				case CLOSED:
					throw new IOException("Connection closed");
				case OK:
					retval = appRecvBuffer.position();
					break;
				default:
					break;
			}
			if(!handleSSLHandshake2()) {
				loop = false; 
				retval = 0;
			}
				
		}
		return retval;
	} 
	
	
	private void write() throws IOException{
		//System.out.println("Doing a write ");
		
		if(!isHanshakeDone) {
			flush();
			if(!handleSSLHandshake2())
				return ; 
		}else {
			
		}
		
		if(appSendBuffer.hasRemaining()) {
			boolean loop = true;
			while (loop) {
				appSendBuffer.flip();
				SSLEngineResult engineResult = engine.wrap(appSendBuffer,netSendBuffer);
				appSendBuffer.compact();
				switch (engineResult.getStatus()) {
					case BUFFER_UNDERFLOW:
						// This should not occur
//						throw new IOException("No data to write");
						loop = false;
						break;
					case BUFFER_OVERFLOW:
						// The data buffer is full, flush it out
						flush();
						break;
						//continue;
					case CLOSED:
						// I would have thought the SSLEngine would do this itself,
						// so this is probably unreachable.
						throw new IOException("Connection closed");
					case OK:
						loop = false;
//						flush();
						break;
				}
				if(!handleSSLHandshake2())
					return ;
			}
		}
		flush();
	}
	
	
	public void doTransfer(ByteBuffer ins) throws IOException{
		byte b[] = new byte[512];
		while (ins.hasRemaining() ) {
			
			int canwite = appSendBuffer.capacity() - appSendBuffer.position();
			//.out.println(canwite);
			if (canwite <= 0) {
				write();
			}else {
				int toread = Math.min(512, ins.remaining());
				ins.get(b, 0, toread);
				appSendBuffer.put(b, 0, toread);
			}
		}
		write();
	}
	
	
	protected boolean handleSSLHandshake2() throws IOException {
		// We need to make sure the handshake process finished as a whole
//		boolean proceed = false;
		SSLEngineResult engineResult = null;
		isHanshakeDone = false;
		while (true) {
			switch(engine.getHandshakeStatus()) {
			case NOT_HANDSHAKING:
			case FINISHED:
				isHanshakeDone = true;
				return true;
			case NEED_TASK:
				Executor exec = 
					  Executors.newSingleThreadExecutor();
				Runnable task;
				  while ((task=engine.getDelegatedTask()) != null)
				  {
				    exec.execute(task);
				  }
				continue;
			case NEED_WRAP:
				// We need to call a wrap on the engine
				appSendBuffer.flip();
				engineResult = engine.wrap(appSendBuffer,netSendBuffer);
				appSendBuffer.compact();
				if(engineResult.getStatus() == Status.BUFFER_OVERFLOW || engineResult.getStatus() == Status.OK) { // The enigne sys we need to flush the current buffer into the network
					// So just set the selector to the write mode
					channel.register(selector, SelectionKey.OP_WRITE);
					selector.wakeup();
					//System.out.println("Handshake wants to do a write ");
					return false;
				}else if (engineResult.getStatus() == Status.CLOSED){
					throw new IOException ("Connection closed");
				}else {
					continue;
				}
				
			case NEED_UNWRAP:
				// We need to call unwarap method of the engine
				netRecvBuffer.flip();
				engineResult = engine.unwrap(netRecvBuffer,appRecvBuffer);
				netRecvBuffer.compact();
				//System.out.println(engine.isInboundDone());
				if(engineResult.getStatus() == Status.BUFFER_UNDERFLOW) {
					if(!engine.isInboundDone()) {
						channel.register(selector, SelectionKey.OP_READ);
						selector.wakeup();
						//System.out.println("Handshake wants to do a read ");
						return false;
					}else
						continue;
				}else if (engineResult.getStatus() == Status.CLOSED){
					throw new IOException ("Connection closed");
				}else {
					continue;
				}
			}
		}
	}
	
	
	protected boolean isHandshakeDone() {
		return isHanshakeDone;
	}
	
	private int flush() throws IOException{
		//System.out.println(netSendBuffer.position());
		netSendBuffer.flip();
		int	count = 0; 
		while (netSendBuffer.hasRemaining()) 
			count += channel.write(netSendBuffer);
		netSendBuffer.compact();
		//System.out.println(count);
		return count;
	}
	
	//Need to write the handler stop method;
	public void stop() throws IOException{
		engine.closeInbound();
		engine.closeOutbound();
	}
	
}

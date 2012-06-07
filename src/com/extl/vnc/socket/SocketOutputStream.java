package com.extl.vnc.socket;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class SocketOutputStream extends OutputStream{

	private ByteArrayOutputStream holder = null;
	private SocketClient client = null; 
	private boolean stremClosed = false;
	
	private static final int MAX_BUFF_SIZE = 1024;
	
	SocketOutputStream(SocketClient cli) throws IOException {
		holder = new ByteArrayOutputStream();
		client = cli;
	}
	
	@Override
	public void write(int b) throws IOException {
		if(stremClosed) {
			throw new IOException("Write stream closed");
		}
		synchronized (holder) {
			holder.write(b);
			checkAndFlush();
		}
		
	}

	@Override
	public void write(byte[] arg0) throws IOException {
		if(stremClosed) {
			throw new IOException("Write stream closed");
		}
		synchronized (holder) {
			holder.write(arg0);
			checkAndFlush();
		}
		
	}
	
	@Override
	public void write(byte[] b, int off, int len) throws IOException {
		if(stremClosed) {
			throw new IOException("Write stream closed");
		}
		synchronized (holder) {
			holder.write(b, off, len);
			checkAndFlush();
		}
		
	}
	
	@Override
	public void flush() throws IOException {
		if(stremClosed) {
			throw new IOException("Write stream closed");
		}
		client.sendNow();
	}
	
	protected ByteBuffer getByteBuffer() {
		ByteBuffer buff = null;
		synchronized (holder) {
			buff = ByteBuffer.wrap(holder.toByteArray());
			holder.reset();
		}
		return buff;
	}
	
	@Override
	public void close() throws IOException {
		if(stremClosed) {
			throw new IOException("Write stream closed");
		}
		stremClosed = true;
	}
	
	private void checkAndFlush() throws IOException{
		if (holder.size() > MAX_BUFF_SIZE)
			flush();
	}
}

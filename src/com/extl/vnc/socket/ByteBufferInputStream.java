package com.extl.vnc.socket;

import java.io.IOException;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Vector;

public class ByteBufferInputStream extends InputStream{

	private Vector<ByteBuffer> buffers ; 
	private ByteBuffer currentBuff = null;
	private boolean stremClosed = false;
	
	public ByteBufferInputStream () {
		buffers = new Vector<ByteBuffer>();
	}
	
	@Override
	public int read() throws IOException {
		while(true) {
			if(stremClosed) {
				throw new IOException("Read stream closed");
			}
			if(currentBuff!=null && currentBuff.hasRemaining()) {
				return  (0xFF & currentBuff.get());
			}
			if(buffers.size() > 0) {
				currentBuff = buffers.remove(0);
			}else {
				try {
					synchronized (this) {
						this.wait();
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		
	}
	
	@Override
	public int read(byte[] b) throws IOException {
		return read(b, 0, b.length);
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int read_size = 0;
		int read_length = 0;
		read_length = len;
		while (true) {
			
			if(stremClosed) {
				throw new IOException("Read stream closed");
			}
			
			if(currentBuff != null ) {
				if(currentBuff.remaining()>0) {
					int toread = Math.min(read_length, currentBuff.remaining());
					
					currentBuff.get(b, off, toread);
					read_size +=toread;
					if(read_size == len) {
						break;
					}else {
						off += (read_size);
						read_length -= toread;
						currentBuff = null;
						continue;
					}
				}else {
					currentBuff = null;
					continue;
				}
			}else {
				if(buffers.size() > 0) {
					currentBuff = buffers.remove(0);
				}else {
					try {
						if(read_size == 0 ) {
							synchronized (this) {
								wait();
							}
						}
						else {
							break;
						}
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}
		return read_size;
	}
	
	@Override
	public int available() throws IOException {
		while (true) {
			
			if(stremClosed) {
				throw new IOException("Read stream closed");
			}
			
			if(currentBuff!=null && currentBuff.hasRemaining()) {
				return currentBuff.remaining();
			}else {
				if(buffers.size() > 0) {
					currentBuff = buffers.remove(0);
				}else {
					return 0;
				}
			}
		}
	}
	
	public void addToReadQueue(ByteBuffer buffer){
		buffer.rewind();
		buffers.add(buffer);
		synchronized (this) {
			notifyAll();
		}
		
	}
	
	@Override
	public void close() throws IOException {
		stremClosed = true;
		synchronized (this) {
			notifyAll();
		}
		
	}
	
}

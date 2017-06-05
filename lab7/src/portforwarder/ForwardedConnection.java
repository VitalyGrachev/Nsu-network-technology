package portforwarder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class ForwardedConnection
{
	private static final int BUF_SIZE = 4096;
	private Selector selector;
	private SocketChannel[] channels = new SocketChannel[2];
	private ByteBuffer[] dataBuffers = new ByteBuffer[2];
	private boolean[] receivedEOF = new boolean[2];

	public ForwardedConnection(Selector selector, SocketChannel channel, SocketChannel otherChannel)
	{
		this.selector = selector;
		channels[0] = channel;
		channels[1] = otherChannel;
		dataBuffers[0] = ByteBuffer.allocate(BUF_SIZE);
		dataBuffers[1] = ByteBuffer.allocate(BUF_SIZE);
		dataBuffers[0].limit(0);
		dataBuffers[1].limit(0);
	}

	public void transferFrom(SocketChannel channel) throws IOException
	{
		int peerIdx = getPeerIdx(channel);
		recvDataFrom(peerIdx);
		sendDataTo(getOtherPeerIdx(peerIdx));
	}

	public void recvDataFrom(SocketChannel channel) throws IOException
	{
		recvDataFrom(getPeerIdx(channel));
	}

	public void sendDataTo(SocketChannel channel) throws IOException
	{
		sendDataTo(getPeerIdx(channel));
	}

	public SocketChannel getOtherPeer(SocketChannel channel)
	{
		return channels[getOtherPeerIdx(getPeerIdx(channel))];
	}

	public void close()
	{
		try
		{
			channels[0].close();
		}
		catch(IOException e)
		{
		}
		try
		{
			channels[1].close();
		}
		catch(IOException e)
		{
		}
	}

	private void recvDataFrom(int peerIdx) throws IOException
	{
		dataBuffers[peerIdx].compact();
		int bytesRead = channels[peerIdx].read(dataBuffers[peerIdx]);
		dataBuffers[peerIdx].flip();
		if(bytesRead == -1)
		{
			SelectionKey key = channels[peerIdx].keyFor(selector);
			int interestOps = key.interestOps();
			key.interestOps(interestOps & ~SelectionKey.OP_READ);

			receivedEOF[peerIdx] = true;
		}
	}

	private void sendDataTo(int peerIdx) throws IOException
	{
		int srcPeerIdx = getOtherPeerIdx(peerIdx);
		channels[peerIdx].write(dataBuffers[srcPeerIdx]);
		boolean allDataSent = !dataBuffers[srcPeerIdx].hasRemaining();

		SelectionKey key = channels[peerIdx].keyFor(selector);
		int interestOps = key.interestOps();
		if(allDataSent && (interestOps & SelectionKey.OP_WRITE) != 0)
		{
			key.interestOps(interestOps & ~SelectionKey.OP_WRITE);
		}
		if(!allDataSent && (interestOps & SelectionKey.OP_WRITE) == 0)
		{
			key.interestOps(interestOps | SelectionKey.OP_WRITE);
		}
		if(checkIfCanBeClosed())
		{
			close();
		}
	}

	private int getPeerIdx(SocketChannel channel)
	{
		for(int i = 0; i < 2; i++)
		{
			if(channels[i].equals(channel))
			{
				return i;
			}
		}
		throw new IllegalArgumentException("Channel doesn't belong to this connection.");
	}

	private int getOtherPeerIdx(int peerIdx)
	{
		return (peerIdx + 1) % 2;
	}

	private boolean checkIfCanBeClosed()
	{
		return (receivedEOF[0] || receivedEOF[1]) &&
		       (!receivedEOF[0] || !dataBuffers[0].hasRemaining()) &&
		       (!receivedEOF[1] || !dataBuffers[1].hasRemaining());
	}
}

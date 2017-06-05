package md5crack;

import java.io.EOFException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public abstract class SessionHandler
{
	protected ByteBuffer recvBuffer;
	protected ByteBuffer sendBuffer;
	private boolean fullyReceived = false;

	public void receiveMessage(SocketChannel channel) throws Exception
	{
		if(recvBuffer.hasRemaining())
		{
			if(channel.read(recvBuffer) == -1) throw new EOFException("Unexpected eof.");
		}

		if(!recvBuffer.hasRemaining())
		{
			parseRecvBuffer();
		}
	}

	public void sendMessage(SocketChannel channel) throws Exception
	{
		if(sendBuffer.hasRemaining())
		{
			channel.write(sendBuffer);
		}
	}

	public void startOver()
	{
		sendBuffer.rewind();
		recvBuffer.rewind();
	}

	public boolean hasFullySent()
	{
		return !sendBuffer.hasRemaining();
	}

	public boolean hasFullyReceived()
	{
		return fullyReceived;
	}

	protected void setFullyReceived(boolean fullyReceived)
	{
		this.fullyReceived = fullyReceived;
	}

	protected abstract void parseRecvBuffer();
}

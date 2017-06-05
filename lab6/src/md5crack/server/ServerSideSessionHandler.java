package md5crack.server;

import md5crack.*;

import java.nio.ByteBuffer;
import java.util.UUID;

public class ServerSideSessionHandler extends md5crack.SessionHandler
{
	private long crackedStringIdx = CommonData.HASH_NOT_CRACKED;
	private UUID clientID;
	private Range range;

	public ServerSideSessionHandler()
	{
		recvBuffer = ByteBuffer.allocate(3 * CommonData.SIZEOF_LONG);
		sendBuffer = ByteBuffer.allocate(2 * CommonData.SIZEOF_LONG + CommonData.HASH_LENGTH);
	}

	public long getCrackedStringIdx()
	{
		return crackedStringIdx;
	}

	public UUID getClientID()
	{
		return clientID;
	}

	public void setDataToSend(Range range, byte[] hashToCrack)
	{
		this.range = range;
		if(range == null)
		{
			sendBuffer.putLong(CommonData.NO_JOB_TO_DO);
			sendBuffer.putLong(CommonData.NO_JOB_TO_DO);
		}
		else
		{
			sendBuffer.putLong(range.start);
			sendBuffer.putLong(range.end);
		}
		sendBuffer.put(hashToCrack);
		sendBuffer.flip();
	}

	public Range getRange()
	{
		return range;
	}

	@Override
	protected void parseRecvBuffer()
	{
		recvBuffer.rewind();
		long mostSignBits = recvBuffer.getLong();
		long leastSignBits = recvBuffer.getLong();
		crackedStringIdx = recvBuffer.getLong();

		clientID = new UUID(mostSignBits, leastSignBits);
		setFullyReceived(true);
	}
}

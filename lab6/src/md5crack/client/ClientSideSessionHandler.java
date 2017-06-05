package md5crack.client;

import md5crack.CommonData;
import md5crack.Range;
import md5crack.SessionHandler;

import java.nio.ByteBuffer;
import java.util.UUID;

public class ClientSideSessionHandler extends SessionHandler
{
	private Range rangeToCrack;
	private String hashToCrack;

	public ClientSideSessionHandler()
	{
		recvBuffer = ByteBuffer.allocate(2 * CommonData.SIZEOF_LONG + CommonData.HASH_LENGTH);
		sendBuffer = ByteBuffer.allocate(3 * CommonData.SIZEOF_LONG);
	}

	public ClientSideSessionHandler(UUID clientID, long crackedStringIdx)
	{
		this();
		setDataToSend(clientID, crackedStringIdx);
	}

	public void setDataToSend(UUID clientID, long crackedStringIdx)
	{
		sendBuffer.putLong(clientID.getMostSignificantBits());
		sendBuffer.putLong(clientID.getLeastSignificantBits());
		sendBuffer.putLong(crackedStringIdx);
		sendBuffer.flip();
	}

	public Range getRangeToCrack()
	{
		return rangeToCrack;
	}

	public String getHashToCrack()
	{
		return hashToCrack;
	}

	@Override
	protected void parseRecvBuffer()
	{
		recvBuffer.rewind();
		byte[] hashToCrackBytes = new byte[CommonData.HASH_LENGTH];
		long start = recvBuffer.getLong();
		long end = recvBuffer.getLong();
		recvBuffer.get(hashToCrackBytes);

		hashToCrack = new String(hashToCrackBytes, CommonData.CHARSET).toUpperCase();

		if(start == CommonData.NO_JOB_TO_DO ||
		   end == CommonData.NO_JOB_TO_DO)
		{
			rangeToCrack = null;
		}
		else
		{
			rangeToCrack = new Range(start, end);
		}
	}
}

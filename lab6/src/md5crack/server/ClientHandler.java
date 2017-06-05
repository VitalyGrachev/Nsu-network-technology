package md5crack.server;

import md5crack.Range;

import java.util.UUID;

class ClientHandler
{
	public UUID clientID;
	public Range stringRange = null;
	public long lastSeenTime = 0;

	public ClientHandler(UUID clientID)
	{
		this.clientID = clientID;
	}
}

package md5crack.client;

import md5crack.CandidateStringBuilder;
import md5crack.CommonData;
import md5crack.Range;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.xml.bind.DatatypeConverter;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

public class Client implements CommonData
{
	private static Logger logger = LogManager.getLogger("default_logger");

	private UUID clientID;
	private MessageDigest digest;
	private InetSocketAddress serverAddress;

	private String hashToCrack;
	private Range rangeToCrack;
	private long crackedStringIdx = HASH_NOT_CRACKED;

	public Client(InetSocketAddress serverAddress)
	{
		this.serverAddress = serverAddress;
		this.clientID = UUID.randomUUID();
		try
		{
			this.digest = MessageDigest.getInstance(HASH_TYPE);
		}
		catch(NoSuchAlgorithmException e)
		{
			throw new RuntimeException("NO such hash type");
		}
	}

	public static void main(String[] args)
	{
		if(args.length == 2)
		{
			int port = Integer.parseInt(args[1]);
			InetSocketAddress address = new InetSocketAddress(args[0], port);
			Client client = new Client(address);
			client.run();
		}
		else
		{
			System.out.println("Invalid arguments.");
		}
	}

	public void run()
	{
		boolean gotJob = getFirstJob();
		while(gotJob)
		{
			findString();
			gotJob = reportToServerAndGetNewJob();
		}
		logger.info("Client {}: No job to do. Going down...", clientID.toString());
	}

	private boolean sendToServerAndGetJob(ClientSideSessionHandler handler)
	{
		boolean gotJob = false;
		boolean gotServersAnswer = false;
		int reconnect_attempts = 0;

		while(!gotServersAnswer && reconnect_attempts < CLIENT_RECONNECT_MAX)
		{
			logger.info("Client {}: connecting to server {}.", clientID.toString(), serverAddress.toString());
			try(SocketChannel channel = SocketChannel.open(serverAddress))
			{
				handler.startOver();
				handler.sendMessage(channel);
				handler.receiveMessage(channel);
				rangeToCrack = handler.getRangeToCrack();
				hashToCrack = handler.getHashToCrack();

				gotJob = (rangeToCrack != null);
				gotServersAnswer = true;
			}
			catch(Exception e)
			{
				logger.info("Client {}: failed to connect. Will try again in {} milliseconds.", clientID.toString(), CLIENT_RECONNECT_PERIOD);
				try
				{
					Thread.sleep(CLIENT_RECONNECT_PERIOD);
				}
				catch(InterruptedException e1)
				{
					e1.printStackTrace();
				}
			}
			reconnect_attempts++;
		}
		if(!gotServersAnswer && reconnect_attempts == CLIENT_RECONNECT_MAX)
		{
			logger.info("Client {}: couldn't connect to server {}.", clientID.toString(), serverAddress.toString());
		}
		return gotJob;
	}

	private boolean getFirstJob()
	{
		ClientSideSessionHandler handler = new ClientSideSessionHandler(clientID, HASH_NOT_CRACKED);
		return sendToServerAndGetJob(handler);
	}

	private boolean reportToServerAndGetNewJob()
	{
		ClientSideSessionHandler handler = new ClientSideSessionHandler(clientID, crackedStringIdx);
		return sendToServerAndGetJob(handler);
	}

	private void findString()
	{
		CandidateStringBuilder builder = new CandidateStringBuilder(rangeToCrack.start);
		for(long idx = rangeToCrack.start; idx <= rangeToCrack.end; ++idx)
		{
			CandidateStringBuilder.Candidate candidate = builder.getNextCandidate();

			digest.update(candidate.data, 0, candidate.length);
			byte[] hash = digest.digest();
			digest.reset();

			String hexHash = DatatypeConverter.printHexBinary(hash);

			if(idx % 100 == 0)
			{
				String candidateStr = new String(candidate.data, 0, candidate.length, CHARSET);
				logger.info("Client {}: Tried string: '{}'", clientID.toString(), candidateStr);
			}

			if(hexHash.equals(hashToCrack))
			{
				crackedStringIdx = candidate.index;
				String candidateStr = new String(candidate.data, 0, candidate.length, CHARSET);
				logger.info("Client {}: Answer found: {}", clientID.toString(), candidateStr);
				break;
			}
			else
			{
				crackedStringIdx = HASH_NOT_CRACKED;
			}
		}
	}
}

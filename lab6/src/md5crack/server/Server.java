package md5crack.server;

import md5crack.CandidateStringBuilder;
import md5crack.CommonData;
import md5crack.Range;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;

public class Server implements CommonData
{
	private static Logger logger = LogManager.getLogger("default_logger");
	private static final long maxRangeToCrack = 20000;
	private static final long SELECT_TIMEOUT = 3000;
	private static final long JOB_TIMEOUT = 9000;

	private int port;
	private boolean shouldStop = false;
	private Deque<Range> rangesToCrack = new LinkedList<>();
	private Map<UUID, ClientHandler> clients = new HashMap<>();
	private byte[] crackedString = null;
	private byte[] hashToCrack = null;

	public Server(byte[] hashToCrack, int port)
	{
		this.hashToCrack = hashToCrack;
		this.port = port;

		long maxStringIdx = 0;
		long stringsForLength = ALPHABET.length;
		for(int i = 0; i < MAX_STRING_LENGTH; ++i)
		{
			maxStringIdx += stringsForLength;
			stringsForLength *= ALPHABET.length;
		}
		rangesToCrack.add(new Range(0L, maxStringIdx));
	}

	public static void main(String[] args)
	{
		if(args.length == 2)
		{
			byte[] hashToCrack = args[0].getBytes(CHARSET);
			int port = Integer.parseInt(args[1]);
			Server server = new Server(hashToCrack, port);
			server.run();
		}
		else
		{
			System.out.println("Invalid arguments");
		}
	}

	public void run()
	{
		try(ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
		    Selector selector = Selector.open())
		{
			serverSocketChannel.bind(new InetSocketAddress(port));
			serverSocketChannel.configureBlocking(false);
			serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
			logger.info("Server started on port {}", port);

			while(!shouldStop)
			{
				int numReady = selector.select(SELECT_TIMEOUT);

				if(numReady != 0)
				{
					Set<SelectionKey> keys = selector.selectedKeys();
					Iterator<SelectionKey> it = keys.iterator();

					while(it.hasNext())
					{
						SelectionKey key = it.next();

						if(key.isAcceptable())
						{
							acceptClient(selector, key);
						}
						else if(key.isReadable())
						{
							receiveData(key);
						}
						else if(key.isWritable())
						{
							sendReply(key);
						}
					}
					keys.clear();
				}

				addToQueueTimedOutJobs();
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
		clients.clear();
		rangesToCrack.clear();
	}

	private void acceptClient(Selector selector, SelectionKey key)
	{
		ServerSocketChannel acceptor = (ServerSocketChannel) key.channel();
		try
		{
			SocketChannel client = acceptor.accept();
			client.configureBlocking(false);
			client.register(selector, SelectionKey.OP_READ, new ServerSideSessionHandler());
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	private void receiveData(SelectionKey key)
	{
		SocketChannel channel = (SocketChannel) key.channel();
		ServerSideSessionHandler sessionHandler = (ServerSideSessionHandler) key.attachment();
		try
		{
			sessionHandler.receiveMessage(channel);
			if(sessionHandler.hasFullyReceived())
			{
				long crackedStringIdx = sessionHandler.getCrackedStringIdx();
				if(crackedStringIdx != HASH_NOT_CRACKED)
				{
					CandidateStringBuilder.Candidate candidate = CandidateStringBuilder.getByIndex(crackedStringIdx);
					crackedString = Arrays.copyOf(candidate.data, candidate.length);
					System.out.println("Hash was successfully cracked: " + new String(crackedString));
					rangesToCrack.clear();
				}

				Range nextRange = getRangePart();
				if(crackedStringIdx == HASH_NOT_CRACKED && nextRange != null)
				{
					sessionHandler.setDataToSend(nextRange, hashToCrack);
				}
				else
				{
					sessionHandler.setDataToSend(new Range(NO_JOB_TO_DO, NO_JOB_TO_DO), hashToCrack);
				}

				key.interestOps(SelectionKey.OP_WRITE);
				sendRemaining(channel, sessionHandler);
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
			handleTransmissionError(channel, sessionHandler);
		}
	}

	private void sendReply(SelectionKey key)
	{
		SocketChannel channel = (SocketChannel) key.channel();
		ServerSideSessionHandler sessionHandler = (ServerSideSessionHandler) key.attachment();
		try
		{
			sendRemaining(channel, sessionHandler);
		}
		catch(Exception e)
		{
			handleTransmissionError(channel, sessionHandler);
		}
	}

	private void sendRemaining(SocketChannel channel, ServerSideSessionHandler sessionHandler) throws Exception
	{
		sessionHandler.sendMessage(channel);
		if(sessionHandler.hasFullySent())
		{
			channel.close();
			UUID clientID = sessionHandler.getClientID();
			ClientHandler clientHandler = clients.get(clientID);

			if(clientHandler == null)
			{
				clientHandler = new ClientHandler(clientID);
				clients.put(clientID, clientHandler);
			}
			clientHandler.stringRange = sessionHandler.getRange();
			clientHandler.lastSeenTime = System.currentTimeMillis();

			logger.info("Server: commanded to client {} to try strings from #{} to #{}",
					clientID.toString(),
					clientHandler.stringRange.start,
					clientHandler.stringRange.end);

		}
	}

	private Range getRangePart()
	{
		Range range = rangesToCrack.pollFirst();
		if(range == null || range.getLength() <= maxRangeToCrack)
		{
			return range;
		}
		Range leftover = new Range(range.start + maxRangeToCrack, range.end);
		rangesToCrack.offerFirst(leftover);
		return new Range(range.start, range.start + maxRangeToCrack - 1);
	}

	private void handleTransmissionError(SocketChannel channel, ServerSideSessionHandler sessionHandler)
	{
		Range range = sessionHandler.getRange();
		if(range != null)
		{
			rangesToCrack.offerFirst(range);
		}
		try
		{
			channel.close();
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}

	private void addToQueueTimedOutJobs()
	{
		boolean allClientsTimeOutJobs = true;
		long currentTime = System.currentTimeMillis();
		Iterator<Map.Entry<UUID, ClientHandler>> it = clients.entrySet().iterator();
		while(it.hasNext())
		{
			Map.Entry<UUID, ClientHandler> client = it.next();
			ClientHandler handler = client.getValue();
			long lastSeenTime = handler.lastSeenTime;
			if(lastSeenTime + JOB_TIMEOUT < currentTime)
			{
				Range range = handler.stringRange;
				if(range != null)
				{
					rangesToCrack.offerFirst(range);
					handler.stringRange = null;
				}
				it.remove();
			}
			else
			{
				allClientsTimeOutJobs = false;
			}
		}

		if(allClientsTimeOutJobs && rangesToCrack.isEmpty())
		{
			shouldStop = true;
		}
	}
}

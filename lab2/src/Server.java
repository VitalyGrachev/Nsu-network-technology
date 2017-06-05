
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

public class Server
{
	private final static int PORT = 14202;
	private final static int TIMEOUT = 1000; // ms == 1s
	private final static int PERIOD = 5000; // ms == 5s
	private final static int BUF_SIZE = 1024; // byte


	private Selector selector;
	private ServerSocketChannel serverSocketChannel;
	private ByteBuffer buffer;

	private Server() throws IOException
	{
		selector = Selector.open();
		serverSocketChannel = ServerSocketChannel.open();
		buffer = ByteBuffer.allocate(BUF_SIZE);
	}

	private void run()
	{
		try
		{
			serverSocketChannel.bind(new InetSocketAddress(PORT));
			serverSocketChannel.configureBlocking(false);
			serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

			while(true)
			{
				int numReady = selector.select(TIMEOUT);

				if(numReady != 0)
				{
					Set<SelectionKey> keys = selector.selectedKeys();
					Iterator<SelectionKey> it = keys.iterator();

					while(it.hasNext())
					{
						SelectionKey key = it.next();

						if(key.isReadable())
						{
							receiveData(key);
						}
						else if(key.isAcceptable())
						{
							acceptClient(key);
						}
					}
					keys.clear();
				}
			}
		}
		catch(IOException e)
		{
			System.err.println("Run failed.");
			e.printStackTrace();
		}
	}

	private void acceptClient(SelectionKey connectionKey)
	{
		try
		{
			ServerSocketChannel acceptor = (ServerSocketChannel) connectionKey.channel();
			SocketChannel client = acceptor.accept();
			client.configureBlocking(false);
			client.register(selector, SelectionKey.OP_READ, new ConnectionData());
			System.out.println(client.getRemoteAddress().toString()+" connected.");
		}
		catch(IOException e)
		{
			System.err.println("Acception of new client failed.");
		}
	}

	private void receiveData(SelectionKey connectionKey)
	{
		SocketChannel client = (SocketChannel) connectionKey.channel();
		try
		{
			if(!buffer.hasRemaining())
			{
				buffer.rewind();
			}
			int bytesRead = client.read(buffer);

			if(bytesRead == -1)
			{
				System.out.println(client.getRemoteAddress().toString()+" disconnected.");
				client.close();
				return;
			}

			long curTime = System.currentTimeMillis();
			ConnectionData data = (ConnectionData) connectionKey.attachment();
			data.bytesReceived += bytesRead;
			if(data.lastRecvTime + PERIOD <= curTime)
			{
				System.out.print(client.getRemoteAddress().toString() + ": ");
				System.out.print(data.bytesReceived * 1000 / (curTime - data.lastRecvTime) / 1024 + " KB/sec (");
				System.out.println(8 * data.bytesReceived * 1000 / (curTime - data.lastRecvTime) / 1024 + " Kbit/sec)");
				data.lastRecvTime = curTime;
				data.bytesReceived = 0;
			}
		}
		catch(ClosedChannelException e)
		{
			System.err.println("Channel closed.");
			try
			{
				client.close();
			}
			catch(IOException e1)
			{
				e1.printStackTrace();
			}
		}
		catch(IOException e)
		{
			System.err.println("Data receive failed.");
			try
			{
				client.close();
			}
			catch(IOException e1)
			{
				e1.printStackTrace();
			}
		}
	}

	public static void main(String[] args)
	{
		try
		{
			Server server = new Server();
			server.run();
		}
		catch(IOException e)
		{
			System.err.println("Initialization failed.");
			return;
		}
	}

	private class ConnectionData
	{
		public long bytesReceived = 0;
		public long lastRecvTime = System.currentTimeMillis();
	}
}

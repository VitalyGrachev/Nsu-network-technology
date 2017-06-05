package portforwarder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class Forwarder
{
	private int lPort;
	private InetSocketAddress dstAddress;
	private boolean shouldStop = false;

	public Forwarder(int lPort, InetSocketAddress dstAddress)
	{
		this.lPort = lPort;
		this.dstAddress = dstAddress;
	}

	public static void main(String[] args)
	{
		if(args.length == 3)
		{
			int lPort = Integer.parseInt(args[0]);
			int rPort = Integer.parseInt(args[2]);
			InetSocketAddress address = new InetSocketAddress(args[1], rPort);

			Forwarder forwarder = new Forwarder(lPort, address);
			forwarder.run();
		}
		else
		{
			System.out.println("Invalid arguments.");
		}
	}

	public void run()
	{
		try(Selector selector = Selector.open();
		    ServerSocketChannel serverSocketChannel = ServerSocketChannel.open())
		{
			serverSocketChannel.bind(new InetSocketAddress(lPort));
			serverSocketChannel.configureBlocking(false);
			serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

			while(!shouldStop)
			{
				selector.select();

				Set<SelectionKey> keys = selector.selectedKeys();
				Iterator<SelectionKey> it = keys.iterator();
				while(it.hasNext())
				{
					SelectionKey key = it.next();

					if(key.isAcceptable())
					{
						acceptConnection(selector, key);
					}
					else if(key.isReadable())
					{
						transferData(selector, key);
					}
					else if(key.isWritable())
					{
						sendData(selector, key);
					}
				}

				keys.clear();
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	private void acceptConnection(Selector selector, SelectionKey key)
	{
		ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
		SocketChannel srcChannel = null;
		SocketChannel dstChannel = null;
		try
		{
			srcChannel = serverSocketChannel.accept();
			dstChannel = SocketChannel.open(dstAddress);

			srcChannel.configureBlocking(false);
			dstChannel.configureBlocking(false);

			ForwardedConnection connection = new ForwardedConnection(selector, srcChannel, dstChannel);
			srcChannel.register(selector, SelectionKey.OP_READ, connection);
			dstChannel.register(selector, SelectionKey.OP_READ, connection);
		}
		catch(Exception e)
		{
			if(srcChannel != null)
			{
				try
				{
					srcChannel.close();
				}
				catch(IOException e1)
				{}
			}
			if(dstChannel != null)
			{
				try
				{
					dstChannel.close();
				}
				catch(IOException e1)
				{}
			}
		}
	}

	private void transferData(Selector selector, SelectionKey key)
	{
		SocketChannel channel = (SocketChannel) key.channel();
		ForwardedConnection connection = (ForwardedConnection) key.attachment();
		try
		{
			connection.transferFrom(channel);
		}
		catch(Exception e)
		{
			connection.close();
		}
	}

	private void sendData(Selector selector, SelectionKey key)
	{
		SocketChannel channel = (SocketChannel) key.channel();
		ForwardedConnection connection = (ForwardedConnection) key.attachment();
		try
		{
			connection.sendDataTo(channel);
		}
		catch(Exception e)
		{
			connection.close();
		}
	}
}

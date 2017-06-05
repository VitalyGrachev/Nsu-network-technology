import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Scanner;
import java.util.Set;

public class Server implements FileTransferrer
{
	private Selector selector;
	private int port;
	private File uploadsDir;

	public Server(int port)
	{
		this.port = port;
		uploadsDir = new File(System.getProperty("user.dir") + File.separatorChar + "uploads");
		if(!uploadsDir.exists())
		{
			uploadsDir.mkdir();
		}
		if(!uploadsDir.isDirectory())
		{
			throw new RuntimeException("Uploads is not directory.");
		}
	}

	public void run()
	{
		try(ServerSocketChannel serverSocketChannel = ServerSocketChannel.open())
		{
			selector = Selector.open();
			serverSocketChannel.bind(new InetSocketAddress(port));
			serverSocketChannel.configureBlocking(false);
			serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);

			while(true)
			{
				int numReady = selector.select();

				if(numReady != 0)
				{
					Set<SelectionKey> keys = selector.selectedKeys();
					Iterator<SelectionKey> it = keys.iterator();

					while(it.hasNext())
					{
						SelectionKey key = it.next();

						if(key.isWritable())
						{
							sendStatus(key);
						}
						else if(key.isReadable())
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
			System.out.println("Server stopped. " + e.getMessage());
		}
	}

	private void acceptClient(SelectionKey connectionKey)
	{
		try
		{
			ServerSocketChannel acceptor = (ServerSocketChannel) connectionKey.channel();
			SocketChannel client = acceptor.accept();
			client.configureBlocking(false);
			client.register(selector, SelectionKey.OP_READ, new ClientData());
		}
		catch(Exception e)
		{
			System.err.println("Acception of new client failed.");
		}
	}

	private void receiveData(SelectionKey connectionKey)
	{
		SocketChannel client = (SocketChannel) connectionKey.channel();
		ClientData clientData = (ClientData) connectionKey.attachment();
		try
		{
			clientData.receive(client);
		}
		catch(Exception e)
		{
			try
			{
				if(clientData.fileChannel != null)
				{
					clientData.fileChannel.close();
				}
				client.close();
			}
			catch(IOException e1)
			{
			}
			System.out.println("Error occurred. Client disconnected.");
		}
	}

	private void sendStatus(SelectionKey connectionKey)
	{
		SocketChannel client = (SocketChannel) connectionKey.channel();
		ClientData clientData = (ClientData) connectionKey.attachment();
		try
		{
			clientData.sendStatus(client);
		}
		catch(Exception e)
		{
			try
			{
				if(clientData.fileChannel != null)
				{
					clientData.fileChannel.close();
				}
				client.close();
			}
			catch(IOException e1)
			{
			}
			System.out.println("Error occurred. Client disconnected.");
		}
	}

	public static void main(String[] args)
	{
		System.out.println("port");
		Scanner scan = new Scanner(System.in);
		int port = scan.nextInt();

		Server app = new Server(port);
		app.run();
	}

	private class ClientData
	{
		private final static int INT_SIZE = 4;
		private final static int LONG_SIZE = 8;

		private boolean successfullyReceived = false;
		private File file;
		private FileChannel fileChannel;
		private long expectedFileSize;
		private long totalBytesReceived = 0;
		private ByteBuffer buffer;

		public ClientData()
		{
			buffer = ByteBuffer.allocate(BUFSIZE);
		}

		public void receive(SocketChannel client) throws IOException
		{
			int bytesReceived;
			bytesReceived = client.read(buffer);
			if(bytesReceived == -1)
			{
				throw new EOFException("Unexpected socket eof reached.");
			}
			parseMessage(client);
		}

		private void parseMessage(SocketChannel client) throws IOException
		{
			if(buffer.position() >= 1)
			{
				byte msg_type = buffer.get(0);
				switch(msg_type)
				{
					case MSG_TYPE_FILENAME:
						parseFileName(client);
						break;
					case MSG_TYPE_FILELEN:
						parseFileLen(client);
						break;
					case MSG_TYPE_DATABLOCK:
						parseDataBlock(client);
						break;
					case MSG_TYPE_FILEEND:
						parseFileEnd(client);
						break;
					default:
						throw new IOException("Invalid message type");
				}
			}
		}

		private void parseFileName(SocketChannel client) throws IOException
		{
			if(buffer.position() >= 1 + INT_SIZE)
			{
				int fileNameLen = buffer.getInt(1);
				if(fileNameLen < 0 || fileNameLen > 4096)
				{
					throw new IOException("Invalid data received " + fileNameLen);
				}
				if(buffer.position() >= 1 + INT_SIZE + fileNameLen)
				{
					byte[] fileNameBytes = new byte[fileNameLen];

					int lastPos = buffer.position();
					buffer.position(1 + INT_SIZE);
					buffer.limit(1 + INT_SIZE + fileNameLen);

					buffer.get(fileNameBytes);

					buffer.position(1 + INT_SIZE + fileNameLen);
					buffer.limit(lastPos);
					buffer.compact();

					String fileName = new String(fileNameBytes);
					file = new File(uploadsDir, fileName);
					if(file.exists())
					{
						throw new IOException("File " + fileName + " already exists.");
					}
					file.createNewFile();
					fileChannel = FileChannel.open(file.toPath(), StandardOpenOption.WRITE);

					if(buffer.position() >= 1)
					{
						parseMessage(client);
					}
				}
			}
		}

		private void parseFileLen(SocketChannel client) throws IOException
		{
			if(buffer.position() >= 1 + LONG_SIZE)
			{
				int lastPos = buffer.position();
				expectedFileSize = buffer.getLong(1);
				if(expectedFileSize < 0)
				{
					throw new IOException("Invalid data received " + expectedFileSize);
				}
				buffer.position(1 + LONG_SIZE);
				buffer.limit(lastPos);
				buffer.compact();

				if(buffer.position() >= 1)
				{
					parseMessage(client);
				}
			}
		}

		private void parseDataBlock(SocketChannel client) throws IOException
		{
			if(buffer.position() >= 1 + INT_SIZE)
			{
				int dataBlockLen = buffer.getInt(1);
				if(dataBlockLen < 0)
				{
					throw new IOException("Invalid data received " + dataBlockLen);
				}
				if(buffer.position() >= 1 + INT_SIZE + dataBlockLen)
				{
					int lastPos = buffer.position();

					buffer.position(1 + INT_SIZE);
					buffer.limit(1 + INT_SIZE + dataBlockLen);
					while(buffer.hasRemaining())
					{
						int bytesWrote = fileChannel.write(buffer);
						if(bytesWrote == -1)
						{
							throw new EOFException("Unexpected file eof reached.");
						}
						totalBytesReceived += bytesWrote;
					}
					buffer.position(1 + INT_SIZE + dataBlockLen);
					buffer.limit(lastPos);
					buffer.compact();

					if(buffer.position() >= 1)
					{
						parseMessage(client);
					}
				}
			}
		}

		private void parseFileEnd(SocketChannel client) throws IOException
		{
			successfullyReceived = expectedFileSize == totalBytesReceived;
			if(!sendStatus(client))
			{
				client.register(selector, SelectionKey.OP_WRITE, this);
			}
		}

		private boolean sendStatus(SocketChannel client) throws IOException
		{
			ByteBuffer buf = ByteBuffer.allocate(1);
			buf.put((successfullyReceived ? STATUS_OK : STATUS_FAIL));
			buf.flip();
			int bytesWrote = client.write(buf);
			if(bytesWrote == 1)
			{
				fileChannel.close();
				client.close();
				return true;
			}
			return false;
		}
	}
}

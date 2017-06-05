import java.io.File;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.StandardOpenOption;

public class HttpPoller implements Runnable
{
	private static final int BUF_SIZE = 512;
	private static final String CRLF = "\r\n";
	private static final int hostPort = 80;
	private URL resource;
	private File localCopy;
	private String lastModifiedDate;
	private ByteBuffer buffer;

	public HttpPoller(URL resource, File localCopy)
	{
		this.resource = resource;
		this.localCopy = localCopy;
		this.lastModifiedDate = "Mon, 01 Jan 1980 00:01:01 GMT" + CRLF;
		this.buffer = ByteBuffer.allocate(BUF_SIZE);
	}

	@Override
	public void run()
	{
		String request = "GET " + resource + " HTTP/1.1" + CRLF +
		                 "Host: " + resource.getHost() + CRLF +
		                 "If-Modified-Since: " + lastModifiedDate + CRLF;

		try(SocketChannel socketChannel = SocketChannel.open(new InetSocketAddress(resource.getHost(), hostPort)))
		{
			buffer.clear();
			buffer.put(request.getBytes());
			buffer.flip();
			socketChannel.write(buffer);
			buffer.clear();


			boolean isGoingToRecvFile = false;
			long contentLength = 0;
			String modifiedDate = "";
			String[] sublines;
			String headerLine;

			socketChannel.read(buffer);
			do
			{
				while((headerLine = getLine(buffer)) == null)
				{
					socketChannel.read(buffer);
				}

				if(headerLine.startsWith("HTTP/1.1"))
				{
					sublines = headerLine.split(" ");
					int statusCode = Integer.parseInt(sublines[1]);
					if(200 <= statusCode && statusCode < 300)
					{
						isGoingToRecvFile = true;
					}
					else
					{
						isGoingToRecvFile = false;
						break;
					}
				}
				else if(headerLine.startsWith("Last-Modified: "))
				{
					modifiedDate = headerLine.substring("Last-Modified: ".length());
					if(!modifiedDate.equals(lastModifiedDate))
					{
						isGoingToRecvFile = true;
					}
					else
					{
						isGoingToRecvFile = false;
						break;
					}
				}
				else if(headerLine.startsWith("Content-Length: "))
				{
					sublines = headerLine.split("\\s");
					contentLength = Long.parseLong(sublines[1]);
				}
			}
			while(!CRLF.equals(headerLine));

			if(isGoingToRecvFile)
			{
				localCopy.createNewFile();
				try(FileChannel fc = FileChannel.open(localCopy.toPath(), StandardOpenOption.WRITE))
				{
					if(contentLength > 0)
					{
						long bytesWrote = 0;
						do
						{
							buffer.flip();
							bytesWrote += fc.write(buffer);
							buffer.clear();
						}
						while(bytesWrote < contentLength &&
						      socketChannel.read(buffer) != -1);

						lastModifiedDate = modifiedDate;
						System.out.println("Http Poller: Successfully downloaded file " + resource.toExternalForm());
					}
				}
				catch(Exception e)
				{
					e.printStackTrace();
				}
			}
			else
			{
				System.out.println("Http Poller: No need to download file " + resource.toExternalForm());
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	private static String getLine(ByteBuffer buffer)
	{
		String stringGot = null;
		byte symbol;
		int dataEnd = buffer.position();
		buffer.flip();

		if(buffer.hasRemaining())
		{
			while((symbol = buffer.get()) != '\n' &&
			      buffer.hasRemaining()) ;

			if(symbol == '\n')
			{
				int nextLineStart = buffer.position();
				byte[] lineBytes = new byte[nextLineStart];
				buffer.rewind();
				buffer.get(lineBytes);
				stringGot = new String(lineBytes);
			}
			else
			{
				buffer.position(0);
			}
			buffer.limit(dataEnd);
		}
		buffer.compact();
		return stringGot;
	}
}

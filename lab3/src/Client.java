import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.file.StandardOpenOption;
import java.util.Scanner;

public class Client implements FileTransferrer
{
	private File file;
	private InetSocketAddress address;
	private long fileSize;
	private long totalBytesTransferred = 0;

	public Client(File file, InetSocketAddress address)
	{
		this.file = file;
		this.address = address;
	}

	private void run() throws FileNotFoundException
	{
		if(!file.exists() || !file.isFile())
		{
			throw new FileNotFoundException(file.getPath());
		}

		fileSize = file.length();
		try(FileChannel fileChannel = FileChannel.open(file.toPath(), StandardOpenOption.READ);
		    SocketChannel socketChannel = SocketChannel.open(address))
		{
			int bytesTransferred;
			ByteBuffer buffer = ByteBuffer.allocate(BUFSIZE);
			String filename = file.getName();

			buffer.put(MSG_TYPE_FILENAME);
			buffer.putInt(filename.length());
			buffer.put(filename.getBytes(Charset.forName("UTF-8")));
			buffer.flip();
			while(buffer.hasRemaining())
			{
				bytesTransferred = socketChannel.write(buffer);
				if(bytesTransferred == -1)
				{
					throw new EOFException("Eof reached");
				}
			}

			buffer.clear();
			buffer.put(MSG_TYPE_FILELEN);
			buffer.putLong(fileSize);
			buffer.flip();
			while(buffer.hasRemaining())
			{
				bytesTransferred = socketChannel.write(buffer);
				if(bytesTransferred == -1)
				{
					throw new EOFException("Unexpected socket eof reached.");
				}
			}

			while(totalBytesTransferred < fileSize)
			{
				if(!buffer.hasRemaining())
				{
					buffer.clear();
					buffer.put(MSG_TYPE_DATABLOCK);
					buffer.putInt(0);                 //  reserves space for datablock size
					int bytesRead = fileChannel.read(buffer);
					if(bytesRead == -1)
					{
						throw new EOFException("Unexpected file eof reached.");
					}
					buffer.putInt(1, bytesRead);
					buffer.flip();
				}
				bytesTransferred = socketChannel.write(buffer);
				if(bytesTransferred == -1)
				{
					throw new EOFException("Unexpected socket eof reached.");
				}
				totalBytesTransferred += bytesTransferred;
			}

			buffer.clear();
			buffer.put(MSG_TYPE_FILEEND);
			buffer.flip();
			while(buffer.hasRemaining())
			{
				bytesTransferred = socketChannel.write(buffer);
				if(bytesTransferred == -1)
				{
					throw new EOFException("Unexpected socket eof reached.");
				}
			}

			ByteBuffer buf = ByteBuffer.allocate(1);
			bytesTransferred = socketChannel.read(buf);
			if(bytesTransferred == -1)
			{
				throw new EOFException("Unexpected socket eof reached.");
			}

			buf.flip();
			byte status = buf.get();
			switch(status)
			{
				case STATUS_OK:
					System.out.println("File successfully transferred.");
					break;
				case STATUS_FAIL:
					System.out.println("File transfer failed.");
					break;
			}
		}
		catch(IOException e)
		{
			System.out.println("Failed to transfer file: " + e.getMessage());
		}
	}

	public static void main(String[] args)
	{
		System.out.println("filePath ip port");
		Scanner scan = new Scanner(System.in);
		String filepath = scan.next();
		String severLocation = scan.next();
		int port = scan.nextInt();

		Client app = new Client(new File(filepath), new InetSocketAddress(severLocation, port));
		try
		{
			app.run();
		}
		catch(FileNotFoundException e)
		{
			System.out.println("File " + e.getMessage() + " not found.");
		}
	}
}

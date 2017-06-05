import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.util.Scanner;

public class Client
{
	private final static int BUF_SIZE = 512; // byte

	private void run(InetSocketAddress address)
	{
		try
		{
			SocketChannel channel = SocketChannel.open(address);
			ByteBuffer buffer = ByteBuffer.allocate(BUF_SIZE);

			while(true)
			{
				while(buffer.hasRemaining())
				{
					channel.write(buffer);
				}
				buffer.rewind();
			}
		}
		catch(ClosedChannelException e)
		{
			System.err.println("Channel closed.");
		}
		catch(IOException e)
		{
			System.err.println("Failed to connect to server. " + e.getMessage());
		}
	}

	public final static void main(String[] args)
	{
		Client client = new Client();
		Scanner scan = new Scanner(System.in);
		System.out.println("Print server address:");
		String addr = scan.next();
		int port = scan.nextInt();
		client.run(new InetSocketAddress(addr, port));
	}
}

import java.net.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class Main
{
	private static final int port = 14202;
	private static final int send_period = 1000;
	private static final int dead_timeout = 2 * send_period;
	private static final String some_data = "Hello!";
	private static final Map<InetAddress, Long> found = new HashMap<>();

	public static void main(String[] args)
	{
		byte[] recv_buf = new byte[255];
		byte[] br_addr = {(byte)255, (byte)255, (byte)255, (byte)255};
		int act_neighbor_count = 0;
		int last_neighbor_count = 0;

		try(DatagramSocket socket = new DatagramSocket(port))
		{
			socket.setSoTimeout(send_period);
			InetAddress br_cast_addr = InetAddress.getByAddress(br_addr);
			DatagramPacket send_packet = new DatagramPacket(some_data.getBytes(), some_data.length(), br_cast_addr, port);
			DatagramPacket recv_packet = new DatagramPacket(recv_buf, 255);
			long last_send_time = 0;
			while(true)
			{
				long cur_time = System.currentTimeMillis();
				if(cur_time >= last_send_time + send_period)
				{
					socket.send(send_packet);
					last_send_time = cur_time;
				}

				try
				{
					socket.receive(recv_packet);
					InetAddress sender_address = recv_packet.getAddress();
					if(found.put(sender_address, System.currentTimeMillis()) == null)
					{
						act_neighbor_count++;
					}
				}
				catch(SocketTimeoutException e)
				{
					// do nothing
				}

				cur_time = System.currentTimeMillis();
				Iterator<Map.Entry<InetAddress, Long>> it = found.entrySet().iterator();
				while(it.hasNext())
				{
					Map.Entry<InetAddress, Long> neighbor = it.next();
					if(neighbor.getValue() + dead_timeout < cur_time)
					{
						it.remove();
						act_neighbor_count--;
					}
				}

				if(last_neighbor_count != act_neighbor_count)
				{
					System.out.print("\rNeighbor count: " + act_neighbor_count);
				}
				last_neighbor_count = act_neighbor_count;
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}
}

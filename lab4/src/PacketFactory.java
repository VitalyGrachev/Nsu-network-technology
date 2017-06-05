import java.net.DatagramPacket;
import java.nio.ByteBuffer;
import java.util.UUID;

public class PacketFactory implements NodeConstants
{
	public static DatagramPacket createTextMsg(byte[] author, byte[] text)
	{
		byte[] contents = new byte[1 +
		                           2 * SIZEOF_LONG +
		                           SIZEOF_INT +
		                           author.length +
		                           SIZEOF_INT +
		                           text.length];
		ByteBuffer buffer = ByteBuffer.wrap(contents);
		buffer.put(MSG_TYPE_TextMessage);
		buffer.putLong(0);
		buffer.putLong(0);
		buffer.putInt(author.length);
		buffer.put(author);
		buffer.putInt(text.length);
		buffer.put(text);

		return new DatagramPacket(contents, contents.length);
	}

	public static DatagramPacket createChildConnect(byte flag)
	{
		byte[] contents = new byte[1 +
		                           2 * SIZEOF_LONG +
		                           1];
		ByteBuffer buffer = ByteBuffer.wrap(contents);
		buffer.put(MSG_TYPE_ChildConnect);
		buffer.putLong(0);
		buffer.putLong(0);
		buffer.put(flag);

		return new DatagramPacket(contents, contents.length);
	}

	public static DatagramPacket createGetChildren(int expectedChildrenCount)
	{
		byte[] contents = new byte[1 +
		                           2 * SIZEOF_LONG +
		                           SIZEOF_INT];
		ByteBuffer buffer = ByteBuffer.wrap(contents);
		buffer.put(MSG_TYPE_GetChildren);
		buffer.putLong(0);
		buffer.putLong(0);
		buffer.putInt(expectedChildrenCount);

		return new DatagramPacket(contents, contents.length);
	}

	public static DatagramPacket createChangeParent(byte[] address, int port)
	{
		byte[] contents = new byte[1 +
		                           2 * SIZEOF_LONG +
		                           address.length +
		                           SIZEOF_INT];
		ByteBuffer buffer = ByteBuffer.wrap(contents);
		buffer.put(MSG_TYPE_ChangeParent);
		buffer.putLong(0);
		buffer.putLong(0);
		buffer.put(address);
		buffer.putInt(port);

		return new DatagramPacket(contents, contents.length);
	}

	public static DatagramPacket createConfirmation(UUID msgID)
	{
		byte[] contents = new byte[1 +
		                           2 * SIZEOF_LONG];
		ByteBuffer buffer = ByteBuffer.wrap(contents);
		buffer.put(MSG_TYPE_Confirmation);
		buffer.putLong(msgID.getLeastSignificantBits());
		buffer.putLong(msgID.getMostSignificantBits());

		return new DatagramPacket(contents, contents.length);
	}

	public static DatagramPacket changePacketUUID(DatagramPacket packet, UUID msgID)
	{
		ByteBuffer buffer = ByteBuffer.wrap(packet.getData());
		buffer.putLong(1, msgID.getLeastSignificantBits());
		buffer.putLong(1 + SIZEOF_LONG, msgID.getMostSignificantBits());
		return packet;
	}
}

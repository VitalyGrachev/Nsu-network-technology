import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.*;

public class Node implements NodeConstants
{
	private static Logger logger = LogManager.getLogger("default_logger");

	private final static byte CONNECT_FLAG_FirstConnect = 0x01;
	private final static byte CONNECT_FLAG_Reconnect = 0x02;

	private final static long CONFIRM_TIMOUT = 500;
	private final static int RECV_TIMEOUT = 500;

	private final static int RESEND_LIMIT = -1;
	private final static int RECV_PACKET_MAX_SIZE = 512;
	private final static byte[] NO_PARENT = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};

	private final static Charset charset = Charset.forName("UTF-8");
	private Random random = new Random();

	private boolean shouldStop = false;
	private boolean isDisconnecting = false;
	private boolean isFormerRoot = false;

	private String strNodeName;
	private byte[] nodeName;
	private int lossPercentage;
	private int port;

	private int connectedChildrenCount = 0;
	private int expectedChildrenCount = 0;

	private NodeData parent;
	private Map<InetSocketAddress, NodeData> connectedNodes = new HashMap<>();
	private LimitedMap<UUID, UUID> receivedMessageIDs;
	LimitedMap.LimitController receivedMsgController = new LimitedMap.LimitController(1000000L);
	LimitedMap.LimitController nonConfirmedMsgController = new LimitedMap.LimitController(1000000L);


	public Node(String nodeName, int port, int lossPercentage, InetSocketAddress parentAddress)
	{
		receivedMessageIDs = new LimitedMap<>(receivedMsgController);
		this.strNodeName = nodeName;
		this.nodeName = nodeName.getBytes(charset);
		this.port = port;
		this.lossPercentage = lossPercentage;
		parent = new NodeData(parentAddress, nonConfirmedMsgController);
		if(parentAddress != null)
		{
			connectedNodes.put(parentAddress, parent);
		}
	}

	public void start()
	{
		try(DatagramSocket socket = new DatagramSocket(port);
		    Scanner scanner = new Scanner(System.in))
		{
			socket.setSoTimeout(RECV_TIMEOUT);
			byte[] recvPacketData = new byte[RECV_PACKET_MAX_SIZE];
			DatagramPacket recvPacket = new DatagramPacket(recvPacketData, RECV_PACKET_MAX_SIZE);

			connectToParent(socket, CONNECT_FLAG_FirstConnect);

			while(!shouldStop)
			{
				if(!isDisconnecting && System.in.available() > 0)
				{
					String line = scanner.nextLine();
					switch(line)
					{
						case "q":
							disconnect(socket);
							break;
						case "n":
							printNeighbors();
							break;
						default:
							sendTextMessage(socket, line);
					}
				}

				try
				{
					socket.receive(recvPacket);
					int randNum = random.nextInt(100);
					if(randNum >= lossPercentage)
					{
						parsePacket(recvPacket, socket);
					}
				}
				catch(SocketTimeoutException e)
				{
					// No incoming packet. It's ok.
				}

				resendAllMessages(socket, System.currentTimeMillis());

				analyseExistenceQuestions();
			}
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}

	public static void main(String[] arguments)
	{
		System.out.println("Usage: <NodeName> <port> <lossPercentage> <(optional)parentIP> <(optional)parentPort>");
		Scanner scanner = new Scanner(System.in);
		String line = scanner.nextLine();
		String[] args = line.split(" ");
		Node node;
		if(args.length == 5)
		{
			node = new Node(
					args[0],
					Integer.parseInt(args[1]),
					Integer.parseInt(args[2]),
					new InetSocketAddress(args[3], Integer.parseInt(args[4])));
		}
		else if(args.length == 3)
		{
			node = new Node(
					args[0],
					Integer.parseInt(args[1]),
					Integer.parseInt(args[2]),
					null);
		}
		else
		{
			System.out.println("Invalid arguments");
			return;
		}
		node.start();
	}

	private void printNeighbors()
	{
		if(parent.address == null)
		{
			System.err.print(strNodeName + ": ROOT\n" + strNodeName + ": Children: ");
		}
		else
		{
			System.err.print(strNodeName + ": Parent: " + parent.address.toString() + "\n Children: ");
		}
		for(InetSocketAddress address : connectedNodes.keySet())
		{
			if(!address.equals(parent.address))
			{
				System.err.print(address.toString() + " ");
			}
		}
		System.err.println();
	}

	private void analyseExistenceQuestions()
	{
		if(isDisconnecting &&
		   expectedChildrenCount == connectedChildrenCount &&
		   nonConfirmedMsgController.getRecordsUsed() == 0)
		{
			shouldStop = true;
		}
	}

	/**
	 * Prints text message with its author.
	 *
	 * @param senderBytes  - author name bytes, encoded using <code>charset</code>
	 * @param messageBytes - text message bytes, encoded using <code>charset</code>
	 */
	private void printMessage(byte[] senderBytes, byte[] messageBytes)
	{
		String sender = new String(senderBytes, charset);
		String message = new String(messageBytes, charset);

		System.out.println(sender + ": " + message);
	}

	/**
	 * Sends again all not confirmed by <code>node</code> messages,
	 * for which time since last send exceeded <code>CONFIRM_TIMEOUT</code>.
	 *
	 * @param socket      - socket, used for sent operations
	 * @param node        - node data object, describing destination node
	 * @param currentTime - current time
	 * @throws IOException
	 */
	private void resendMessagesToNode(DatagramSocket socket,
	                                  NodeData node,
	                                  long currentTime) throws IOException
	{
		InetSocketAddress address = node.address;
		Iterator<Pair<UUID, NotConfirmedMessage>> it = node.notConfirmedByNodeMsgs.iterator();
		while(it.hasNext())
		{
			Pair<UUID, NotConfirmedMessage> messagePair = it.next();

			UUID msgID = messagePair.first;
			NotConfirmedMessage message = messagePair.second;
			if(currentTime >= message.lastSendTime + CONFIRM_TIMOUT)
			{
				DatagramPacket packet = message.packet;
				PacketFactory.changePacketUUID(packet, msgID);
				packet.setSocketAddress(address);
				socket.send(packet);

				message.lastSendTime = System.currentTimeMillis();
				++message.resendCount;

				if(message.resendCount == RESEND_LIMIT)
				{
					it.remove();
				}

				logger.info(strNodeName + ": Resending packet " + msgTypeToString(packet.getData()[0]) +
				            " to " + address.toString() +
				            " with id " + msgID.toString());
			}
		}
	}

	/**
	 * For each connected node sends again all not confirmed messages,
	 * for which time since last send exceeded <code>CONFIRM_TIMEOUT</code>.
	 *
	 * @param socket      - socket, used for sent operations
	 * @param currentTime - current time
	 * @throws IOException
	 */
	private void resendAllMessages(DatagramSocket socket,
	                               long currentTime) throws IOException
	{
		Iterator<Map.Entry<InetSocketAddress, NodeData>> it = connectedNodes.entrySet().iterator();
		while(it.hasNext())
		{
			Map.Entry<InetSocketAddress, NodeData> nodeDataEntry = it.next();
			NodeData node = nodeDataEntry.getValue();
			if(node.shouldBeRemoved && node.notConfirmedByNodeMsgs.size() == 0)
			{
				it.remove();
				if(node != parent)
				{
					--connectedChildrenCount;
					--expectedChildrenCount;
				}
			}
			else
			{
				resendMessagesToNode(socket, node, currentTime);
			}
		}
	}

	/**
	 * Sends packet to given node.
	 *
	 * @param socket - socket, used for sent operations
	 * @param packet - packet to be sent
	 * @param node   - node data object, describing destination node
	 * @throws IOException
	 */
	private void sendPacketTo(DatagramSocket socket,
	                          DatagramPacket packet,
	                          NodeData node) throws IOException
	{
		long cur_time = System.currentTimeMillis();
		UUID msgID = UUID.randomUUID();
		PacketFactory.changePacketUUID(packet, msgID);
		packet.setSocketAddress(node.address);
		socket.send(packet);
		node.notConfirmedByNodeMsgs.put(msgID, new NotConfirmedMessage(packet, cur_time));

		logger.info(strNodeName + ": Sending packet " + msgTypeToString(packet.getData()[0]) +
		            " to " + node.address.toString() +
		            " with id " + msgID.toString());
	}

	/**
	 * Broadcasts packet to all connected nodes.
	 *
	 * @param socket - socket, used for sent operations
	 * @param packet - packet to be sent
	 * @throws IOException
	 */
	private void broadcastPacket(DatagramSocket socket,
	                             DatagramPacket packet) throws IOException
	{
		broadcastPacket(socket, packet, null);
	}

	/**
	 * Broadcasts packet to all connected nodes, except one,
	 * described by node data object <code>dontSendTo</code>.
	 *
	 * @param socket     - socket, used for sent operations
	 * @param packet     - packet to be sent
	 * @param dontSendTo - node data object, describing node, that shouldn't get this packet
	 * @throws IOException
	 */
	private void broadcastPacket(DatagramSocket socket,
	                             DatagramPacket packet,
	                             InetSocketAddress dontSendTo) throws IOException
	{
		for(Map.Entry<InetSocketAddress, NodeData> entry : connectedNodes.entrySet())
		{
			NodeData node = entry.getValue();
			if(!node.address.equals(dontSendTo))
			{
				sendPacketTo(socket, packet, node);
			}
		}
	}

	/**
	 * Sends to parent, if it exists, <code>MSG_TYPE_ChildConnect</code> message
	 * with connect flag <code>flag</code>.
	 * <p>
	 * Connection flag informs parent, if this node is new in chat-tree (CONNECT_FLAG_FirstConnect),
	 * or it was redirected to new parent by disconnecting parent (CONNECT_FLAG_Reconnect).
	 * </p>
	 *
	 * @param socket - socket, used for sent operations
	 * @param flag   - connection flag for message
	 * @throws IOException
	 */
	private void connectToParent(DatagramSocket socket, byte flag) throws IOException
	{
		if(parent.address != null)
		{
			DatagramPacket packet = PacketFactory.createChildConnect(flag);

			sendPacketTo(socket, packet, parent);

			logger.info(strNodeName + ": Connecting to parent " + parent.address.toString());
		}
	}

	/**
	 * Redirects given child to this node's parent by sending
	 * <code>MSG_TYPE_ChangeParent</code> message.
	 *
	 * @param socket - socket, used for sent operations
	 * @param node   - node data object, describing destination node
	 * @throws IOException
	 */
	private void makeChildChangeParent(DatagramSocket socket,
	                                   NodeData node) throws IOException
	{
		byte[] parentAddress = parent.address.getAddress().getAddress();
		int port = parent.address.getPort();
		DatagramPacket packet = PacketFactory.createChangeParent(parentAddress, port);

		sendPacketTo(socket, packet, node);
	}

	private void makeRoot(DatagramSocket socket,
	                      NodeData node) throws IOException
	{
		DatagramPacket packet = PacketFactory.createChangeParent(NO_PARENT, 1);
		sendPacketTo(socket, packet, node);
		parent = node;
		isFormerRoot = true;
		--expectedChildrenCount;
		--connectedChildrenCount;
	}

	/**
	 * Redirects all children to this node's parent by sending
	 * <code>MSG_TYPE_ChangeParent</code> message.
	 *
	 * @param socket - socket, used for sent operations
	 * @throws IOException
	 */
	private void makeChildrenChangeParent(DatagramSocket socket) throws IOException
	{
		byte[] parentAddress = parent.address.getAddress().getAddress();
		int port = parent.address.getPort();
		DatagramPacket packet = PacketFactory.createChangeParent(parentAddress, port);

		broadcastPacket(socket, packet, parent.address);
	}

	private void offerChildrenToNode(DatagramSocket socket,
	                                 NodeData node) throws IOException
	{
		DatagramPacket packet = PacketFactory.createGetChildren(expectedChildrenCount);

		sendPacketTo(socket, packet, node);
	}

	private void disconnect(DatagramSocket socket) throws IOException
	{
		isDisconnecting = true;
		if(parent.address != null)
		{
			// This node is not root.
			offerChildrenToNode(socket, parent);
		}
		else
		{
			// This node is root.
			if(connectedNodes.isEmpty())
			{
				if(expectedChildrenCount != connectedChildrenCount)
				{
					// Wait expected node to connect and then make it new root.
				}
			}
			else
			{
				NodeData newRoot = connectedNodes.values().iterator().next();
				makeRoot(socket, newRoot);
			}
		}
	}

	/**
	 * Sends to all connected nodes text message, marked <code>nodeName</code> as author.
	 *
	 * @param socket  - socket, used for sent operations
	 * @param message - text to be sent
	 * @throws IOException
	 */
	private void sendTextMessage(DatagramSocket socket, String message) throws IOException
	{
		byte[] msgBytes = message.getBytes(charset);

		DatagramPacket packet = PacketFactory.createTextMsg(nodeName, msgBytes);
		broadcastPacket(socket, packet);
	}

	/**
	 * Sends message <code>MSG_TYPE_Confirmation</code>, confirming receive of message.
	 *
	 * @param socket        - socket, used for sent operations
	 * @param msgID         - UUID of message, which receive is being confirmed
	 * @param senderAddress - destination node address
	 * @throws IOException
	 */
	private void confirmReceive(DatagramSocket socket,
	                            UUID msgID,
	                            InetSocketAddress senderAddress) throws IOException
	{
		DatagramPacket packet = PacketFactory.createConfirmation(msgID);
		packet.setSocketAddress(senderAddress);
		socket.send(packet);

		logger.info(strNodeName + ": Confirming packet from " + senderAddress.toString() +
		            " with id " + msgID.toString());
	}

	private void becomeRoot()
	{
		NodeData formerRoot = parent;
		parent = new NodeData(null, nonConfirmedMsgController);
		// Former root is already contained in connectedNodes,
		// so no need to add it explicitly, but now it becomes a child,
		// so child counters should be incremented.
		++expectedChildrenCount;
		++connectedChildrenCount;
		formerRoot.notConfirmedByNodeMsgs.clear();
	}

	private void removeNode(NodeData node)
	{
		node.notConfirmedByNodeMsgs.clear();
		node.shouldBeRemoved = true;
		connectedNodes.remove(node.address);
	}

	private void removeChild(NodeData child)
	{
		if(child != null)
		{
			removeNode(child);
			--expectedChildrenCount;
			--connectedChildrenCount;
		}
	}

	private void removeParent(NodeData parent)
	{
		removeNode(parent);
	}

	/**
	 * Parses packet, determines its message type and provides proper processing.
	 *
	 * @param packet - packet to be parsed
	 * @param socket - socket, used for sent operations, if it is necessary for processing
	 * @throws IOException
	 */
	private void parsePacket(DatagramPacket packet, DatagramSocket socket) throws IOException
	{
		ByteBuffer buffer = ByteBuffer.wrap(packet.getData());
		InetSocketAddress senderAddress = new InetSocketAddress(packet.getAddress(), packet.getPort());

		byte msgType = buffer.get();
		long uuidLeastSignBits = buffer.getLong();
		long uuidMostSignBits = buffer.getLong();

		UUID msgID = new UUID(uuidMostSignBits, uuidLeastSignBits);

		logger.info(strNodeName + ": Received " + msgTypeToString(msgType) +
		            " from " + senderAddress.toString() +
		            " with id " + msgID.toString());
		switch(msgType)
		{
			case MSG_TYPE_ChildConnect:
				handleChildConnectMsg(msgID, buffer, senderAddress, socket);
				break;
			case MSG_TYPE_ChangeParent:
				handleChangeParentMsg(msgID, buffer, senderAddress, socket);
				break;
			case MSG_TYPE_GetChildren:
				handleGetChildrenMsg(msgID, buffer, senderAddress, socket);
				break;
			case MSG_TYPE_TextMessage:
				handleTextMessageMsg(msgID, buffer, senderAddress, socket);
				break;
			case MSG_TYPE_Confirmation:
				handleConfirmationMsg(msgID, senderAddress, socket);
				break;
		}
	}

	/**
	 * Provides proper processing for <code>MSG_TYPE_ChangeParent</code> messages.
	 *
	 * @param msgID         - message UUID
	 * @param buffer        - buffer, containing message data
	 * @param senderAddress - sender address
	 * @param socket        - socket, used for sent operations
	 * @throws IOException
	 */
	private void handleChangeParentMsg(UUID msgID,
	                                   ByteBuffer buffer,
	                                   InetSocketAddress senderAddress,
	                                   DatagramSocket socket) throws IOException
	{
		NodeData senderNode = connectedNodes.get(senderAddress);
		confirmReceive(socket, msgID, senderAddress);

		if(!receivedMessageIDs.contains(msgID))
		{
			byte[] address_bytes = new byte[4];
			buffer.get(address_bytes);
			int port = buffer.getInt();

			if(Arrays.equals(address_bytes, NO_PARENT))
			{
				becomeRoot();
			}
			else
			{
				removeParent(parent);

				InetSocketAddress newParent = new InetSocketAddress(
						InetAddress.getByAddress(address_bytes), port);
				parent = new NodeData(newParent, nonConfirmedMsgController);
				connectedNodes.put(parent.address, parent);
				connectToParent(socket, CONNECT_FLAG_Reconnect);
			}

			receivedMessageIDs.put(msgID, msgID);
		}
	}

	/**
	 * Provides proper processing for <code>MSG_TYPE_ChildConnect</code> messages.
	 *
	 * @param msgID         - message UUID
	 * @param buffer        - buffer, containing message data
	 * @param senderAddress - sender address
	 * @param socket        - socket, used for sent operations
	 * @throws IOException
	 */
	private void handleChildConnectMsg(UUID msgID,
	                                   ByteBuffer buffer,
	                                   InetSocketAddress senderAddress,
	                                   DatagramSocket socket) throws IOException
	{
		byte flag = buffer.get();
		if(isDisconnecting && flag == CONNECT_FLAG_FirstConnect)
		{
			// Ignore this message.
			// If someone attempts to join chat-tree by connecting
			// to disconnecting node, it's his own fault.
			// No mercy to losers.
			return;
		}
		else
		{
			confirmReceive(socket, msgID, senderAddress);
		}

		if(!receivedMessageIDs.contains(msgID))
		{
			if(flag == CONNECT_FLAG_FirstConnect)
			{
				++expectedChildrenCount;
			}
			++connectedChildrenCount;

			NodeData senderNode = connectedNodes.put(senderAddress,
					new NodeData(senderAddress, nonConfirmedMsgController));

			if(isDisconnecting)
			{
				if(parent.address == null)
				{
					makeRoot(socket, senderNode);
				}
				else
				{
					makeChildChangeParent(socket, senderNode);
				}
			}

			receivedMessageIDs.put(msgID, msgID);
		}
	}

	/**
	 * Provides proper processing for <code>MSG_TYPE_TextMessage</code> messages.
	 *
	 * @param msgID         - message UUID
	 * @param buffer        - buffer, containing message data
	 * @param senderAddress - sender address
	 * @param socket        - socket, used for sent operations
	 * @throws IOException
	 */
	private void handleTextMessageMsg(UUID msgID,
	                                  ByteBuffer buffer,
	                                  InetSocketAddress senderAddress,
	                                  DatagramSocket socket) throws IOException
	{
		confirmReceive(socket, msgID, senderAddress);

		if(!receivedMessageIDs.contains(msgID))
		{
			if(!isDisconnecting)
			{
				int senderNameLen = buffer.getInt();
				byte[] senderName = new byte[senderNameLen];
				buffer.get(senderName);
				int messageLength = buffer.getInt();
				byte[] message = new byte[messageLength];
				buffer.get(message);
				printMessage(senderName, message);

				DatagramPacket packet = PacketFactory.createTextMsg(senderName, message);

				broadcastPacket(socket, packet, senderAddress);
			}

			receivedMessageIDs.put(msgID, msgID);
		}
	}

	/**
	 * Provides proper processing for <code>MSG_TYPE_GetChildren</code> messages.
	 *
	 * @param msgID         - message UUID
	 * @param buffer        - buffer, containing message data
	 * @param senderAddress - sender address
	 * @param socket        - socket, used for sent operations
	 * @throws IOException
	 */
	private void handleGetChildrenMsg(UUID msgID,
	                                  ByteBuffer buffer,
	                                  InetSocketAddress senderAddress,
	                                  DatagramSocket socket) throws IOException
	{
		NodeData senderNode = connectedNodes.get(senderAddress);

		if(!receivedMessageIDs.contains(msgID))
		{
			if(!isDisconnecting)
			{
				// Accept child's children only if isn't disconnecting.
				confirmReceive(socket, msgID, senderAddress);

				expectedChildrenCount += buffer.getInt();
				removeChild(senderNode);

				// Remember as received only if accepted child's children.
				receivedMessageIDs.put(msgID, msgID);
			}
		}
		else
		{
			// If receivedMessageIDS contains in message,
			// then this node already decided to give positive answer to request.
			confirmReceive(socket, msgID, senderAddress);
		}
	}

	/**
	 * Provides proper processing for <code>MSG_TYPE_Confirmation</code> messages.
	 *
	 * @param msgID         - message UUID
	 * @param senderAddress - sender address
	 * @param socket        - socket, used for sent operations
	 * @throws IOException
	 */
	private void handleConfirmationMsg(UUID msgID,
	                                   InetSocketAddress senderAddress,
	                                   DatagramSocket socket) throws IOException
	{
		if(!receivedMessageIDs.contains(msgID))
		{
			NodeData senderNode = connectedNodes.get(senderAddress);
			NotConfirmedMessage message = senderNode.notConfirmedByNodeMsgs.get(msgID);
			if(message != null)
			{
				senderNode.notConfirmedByNodeMsgs.remove(msgID);
				switch(message.msgType)
				{
					case MSG_TYPE_GetChildren:
						makeChildrenChangeParent(socket);
						removeParent(parent);
						break;
					case MSG_TYPE_ChangeParent:
						if(isFormerRoot && senderAddress.equals(parent.address))
						{
							isFormerRoot = false;
							offerChildrenToNode(socket, parent);
						}
						else
						{
							removeChild(senderNode);
						}
						break;
					case MSG_TYPE_ChildConnect:
						if(isDisconnecting)
						{
							disconnect(socket);
						}
						break;
					default:
						// Do nothing.
				}

			}

			receivedMessageIDs.put(msgID, msgID);
		}
	}

	/**
	 * Returns string representation for message type.
	 *
	 * @param msgType - message type
	 * @return string representation for msgType
	 */
	private String msgTypeToString(byte msgType)
	{
		switch(msgType)
		{
			case MSG_TYPE_ChildConnect:
				return "ChildConnect";
			case MSG_TYPE_ChangeParent:
				return "ChangeParent";
			case MSG_TYPE_GetChildren:
				return "GetChildren";
			case MSG_TYPE_TextMessage:
				return "TextMessage";
			case MSG_TYPE_Confirmation:
				return "Confirmation";
		}
		return "Unknown type";
	}

	/**
	 * Describes connected node.
	 */
	private static class NodeData
	{
		public InetSocketAddress address;
		public LimitedMap<UUID, NotConfirmedMessage> notConfirmedByNodeMsgs;
		public boolean shouldBeRemoved = false;

		public NodeData(InetSocketAddress address, LimitedMap.LimitController limitController)
		{
			this.address = address;
			this.notConfirmedByNodeMsgs = new LimitedMap<>(limitController);
		}
	}

	private static class NotConfirmedMessage
	{
		public byte msgType;
		public DatagramPacket packet;
		public long lastSendTime;
		public int resendCount = 0;

		public NotConfirmedMessage(DatagramPacket packet, long lastSendTime)
		{
			this.msgType = packet.getData()[0];
			this.packet = packet;
			this.lastSendTime = lastSendTime;
		}
	}
}

import java.io.*;
import java.net.Socket;

public class POP3Poller implements Runnable
{
	private static final int BUF_SIZE = 512;
	private static final String STATUS_OK = "+OK";
	private static final String STATUS_ERR = "-ERR";
	private String remoteHost;
	private final int remotePort = 110;
	private String username;
	private String password;
	private File localCopy;
	private char[] buffer;

	public POP3Poller(String remoteHost, String username,
	                  String password, File localCopy)
	{
		this.remoteHost = remoteHost;
		this.username = username;
		this.password = password;
		this.localCopy = localCopy;
		this.buffer = new char[BUF_SIZE];
	}

	@Override
	public void run()
	{
		boolean isSessionSeccessful = false;
		int msgNumber = 0;
		String line;
		String[] sublines;
		try(Socket socket = new Socket(remoteHost, remotePort);
		    BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
		    PrintWriter writer = new PrintWriter(socket.getOutputStream(), true))
		{
			if(reader.readLine().startsWith(STATUS_OK))
			{
				writer.println("USER " + username + '@' + remoteHost);
				if(reader.readLine().startsWith(STATUS_OK))
				{
					writer.println("PASS " + password);
					if(reader.readLine().startsWith(STATUS_OK))
					{
						writer.println("STAT");
						if((line = reader.readLine()).startsWith(STATUS_OK))
						{
							sublines = line.split(" ");
							msgNumber = Integer.parseInt(sublines[1]);

							isSessionSeccessful = true;
							if(msgNumber > 0)
							{
								try(FileWriter localMailbox = new FileWriter(localCopy))
								{
									for(int msg = 1; msg <= msgNumber; ++msg)
									{
										putMessageIntoLocalMailbox(msg, localMailbox, reader, writer);
									}
								}
								catch(Exception e)
								{
									isSessionSeccessful = false;
									e.printStackTrace();
								}
							}
						}
					}
				}
			}
			writer.println("QUIT");
			if(isSessionSeccessful)
			{
				System.out.println("POP3: Received " + msgNumber + " messages for" + username + '@' + remoteHost);
			}
			else
			{
				System.out.println("POP3: Fail to check mail for " + username + '@' + remoteHost);
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	private void putMessageIntoLocalMailbox(int msgIdx, FileWriter localMailbox,
	                                        BufferedReader reader, PrintWriter writer) throws IOException
	{
		String line;
		String[] sublines;

		writer.println("RETR " + msgIdx);
		if(!(line = reader.readLine()).startsWith(STATUS_OK)) { writer.println("QUIT"); return;}

		sublines = line.split(" ");
		int msgLength = Integer.parseInt(sublines[1]);

		int totalGot = 0;
		while(totalGot < msgLength)
		{
			int symbolsRead = reader.read(buffer);
			if(symbolsRead == -1) { return; }
			totalGot += symbolsRead;
			if(totalGot >= msgLength)
			{
				symbolsRead -= totalGot - msgLength;
			}
			localMailbox.write(buffer, 0, symbolsRead);
		}
		localMailbox.write("\n\n");

//		writer.println("DELE " + msgIdx);
//		if(!(line = reader.readLine()).startsWith(STATUS_OK)) { writer.println("QUIT"); return;}
	}
}

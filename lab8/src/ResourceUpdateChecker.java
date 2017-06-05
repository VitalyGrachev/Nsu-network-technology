import java.io.*;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ResourceUpdateChecker
{
	private long checkPeriod;
	private String resourceListFile;
	private Timer timer;
	private Executor executor;

	public ResourceUpdateChecker(String resourceListFile, long checkPeriod)
	{
		this.checkPeriod = checkPeriod;
		this.resourceListFile = resourceListFile;
		this.timer = new Timer();
		this.executor = new ThreadPoolExecutor(1, 3, checkPeriod / 2,
		                                       TimeUnit.MILLISECONDS,
		                                       new ArrayBlockingQueue<>(10));
	}

	public void run()
	{
		try
		{
			schedulePollerTasks();
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}

	}

	private void schedulePollerTasks() throws IOException
	{
		long firstCheckDelay = 200;
		try(BufferedReader resourceList =
				    new BufferedReader(
						    new InputStreamReader(
								    new FileInputStream(resourceListFile))))
		{
			String line;
			while((line = resourceList.readLine()) != null)
			{
				String[] parts = line.split(" ");
				if(parts[0].equals("pop3"))
				{
					PollerTask task = new PollerTask(new POP3Poller(parts[1],
					                                                parts[2],
					                                                parts[3],
					                                                new File(parts[4])),
					                                 executor);
					timer.schedule(task, firstCheckDelay, checkPeriod);
				}
				else if(parts[0].equals("http"))
				{
					URL resource = new URL(parts[1]);
					File localCopy = new File(parts[2]);
					PollerTask task = new PollerTask(new HttpPoller(resource, localCopy), executor);
					timer.schedule(task, firstCheckDelay, checkPeriod);
				}
				else
				{
					// Unknown protocol. Skip this line.
				}
			}
		}
	}

	public static void main(String[] args)
	{
		if(args.length == 2)
		{
			ResourceUpdateChecker checker = new ResourceUpdateChecker(args[0], Long.parseLong(args[1]));
			checker.run();
		}
		else
		{
			System.out.println("Invalid arguments.");
		}
	}

	private static class PollerTask extends TimerTask
	{
		private Runnable poller;
		private Executor executor;

		public PollerTask(Runnable poller, Executor executor)
		{
			this.poller = poller;
			this.executor = executor;
		}

		@Override
		public void run()
		{
			executor.execute(poller);
		}
	}
}

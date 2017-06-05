package md5crack;

public class Range
{
	public long start;
	public long end;

	public Range(long start, long end)
	{
		this.start = start;
		this.end = end;
	}

	public long getLength()
	{
		return end - start;
	}
}

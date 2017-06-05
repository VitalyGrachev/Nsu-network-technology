package md5crack;

import java.util.HashMap;
import java.util.Map;

public class CandidateStringBuilder
{
	private long startIdx;
	private Candidate candidate;
	private Map<Byte, Integer> letterToIdx = new HashMap<>();

	public CandidateStringBuilder(long startIdx)
	{
		this.startIdx = startIdx;

		for(int i = 0; i < CommonData.ALPHABET.length; ++i)
		{
			letterToIdx.put(CommonData.ALPHABET[i], i);
		}
	}

	public Candidate getNextCandidate()
	{
		if(candidate == null)
		{
            candidate = getByIndex(startIdx);
		}
		else
		{
			byte[] data = candidate.data;
			int length = candidate.length;
			int maxLetterIdx = CommonData.ALPHABET.length - 1;

			boolean isMaxStringForThisLength = true;
			for(int i = length - 1; i >= 0; --i)
			{
				int letterIdx = letterToIdx.get(data[i]);
				if(letterIdx < maxLetterIdx)
				{
					data[i] = CommonData.ALPHABET[letterIdx + 1];
					for(int j = i + 1; j < length; ++j)
					{
						data[j] = CommonData.ALPHABET[0];
					}
					isMaxStringForThisLength = false;
					break;
				}
			}
			if(isMaxStringForThisLength)
			{
				length = ++candidate.length;
				for(int j = 0; j < length; ++j)
				{
					data[j] = CommonData.ALPHABET[0];
				}
			}

			++candidate.index;
		}
		return candidate;
	}

	public static Candidate getByIndex(long index)
	{
		byte[] data = new byte[CommonData.MAX_STRING_LENGTH];
		int length;
		if(index == 0)
		{
			length = 0;
		}
		else
		{
			length = 1;
			long stringsForLength = CommonData.ALPHABET.length;
			long expectedIdx = stringsForLength;
			while(expectedIdx < index)
			{
				++length;
				stringsForLength *= CommonData.ALPHABET.length;
				expectedIdx += stringsForLength;
			}

			// Now length is determined.
			long tmp = index - 1;
			for(int i = length - 1; i >= 0; --i)
			{
				int letterIdx = (int) (tmp % CommonData.ALPHABET.length);
				data[i] = CommonData.ALPHABET[letterIdx];
				tmp = (tmp / CommonData.ALPHABET.length) - 1;
			}
		}
		return new Candidate(data, index, length);
	}

	public static class Candidate
	{
		public byte[] data;
		public long index;
		public int length;

		public Candidate(byte[] data, long index, int length)
		{
			this.data = data;
			this.index = index;
			this.length = length;
		}
	}
}

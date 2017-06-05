import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class LimitedMap<Key, Value> implements Iterable<Pair<Key, Value>>
{
	private Map<Key, Record<Key, Value>> records = new HashMap<>();
	private Record<Key, Value> oldestRecord = null;
	private Record<Key, Value> newestRecord = null;
	private int recordCount = 0;
	private LimitController limitController;

	public LimitedMap(LimitController limitController)
	{
		this.limitController = limitController;
	}

	public void put(Key key, Value value)
	{
		Record<Key, Value> record = new Record<>(key, value);
		if(!limitController.addRecordIfPossible())
		{
			Record<Key, Value> recordToDelete = oldestRecord;
			oldestRecord = oldestRecord.nextRecord;
			records.remove(recordToDelete.messagePair.first);
		}

		records.put(key, record);
		++recordCount;

		if(newestRecord != null)
		{
			newestRecord.nextRecord = record;
			record.lastRecord = newestRecord;
		}
		else
		{
			oldestRecord = record;
		}
		newestRecord = record;
	}

	public Value get(Key key)
	{
		Record<Key, Value> record = records.get(key);
		return (record == null ? null : record.messagePair.second);
	}

	public boolean contains(Key key)
	{
		return records.containsKey(key);
	}

	public void remove(Key key)
	{
		Record<Key, Value> record = records.get(key);
		removeRecord(record);
	}

	public void clear()
	{
		records.clear();
		while(recordCount-- > 0)
		{
			limitController.removeRecord();
		}
		oldestRecord = null;
		newestRecord = null;
	}

	public int size()
	{
		return recordCount;
	}

	@Override
	public Iterator<Pair<Key, Value>> iterator()
	{
		return new MQIterator(oldestRecord);
	}

	private void removeRecord(Record<Key, Value> record)
	{
		if(record.lastRecord != null)
		{
			record.lastRecord.nextRecord = record.nextRecord;
		}
		else
		{
			// This record is oldest record.
			oldestRecord = record.nextRecord;
		}
		if(record.nextRecord != null)
		{
			record.nextRecord.lastRecord = record.lastRecord;
		}
		else
		{
			// This record is newest record.
			newestRecord = record.lastRecord;
		}

		records.remove(record.messagePair.first);
		limitController.removeRecord();
		--recordCount;
	}

	private static class Record<Key, Value>
	{
		public Pair<Key, Value> messagePair;
		public Record<Key, Value> nextRecord = null;
		public Record<Key, Value> lastRecord = null;

		public Record(Key key, Value message)
		{
			messagePair = new Pair<>(key, message);
		}
	}

	private class MQIterator implements Iterator<Pair<Key, Value>>
	{
		private Record<Key, Value> record;

		MQIterator(Record<Key, Value> record)
		{
			this.record = record;
		}

		@Override
		public boolean hasNext()
		{
			return record != null;
		}

		@Override
		public Pair<Key, Value> next()
		{
			Pair<Key, Value> ret = record.messagePair;
			record = record.nextRecord;
			return ret;
		}

		@Override
		public void remove()
		{
			Record<Key, Value> toRemove = record.lastRecord;
			record = record.nextRecord;
			removeRecord(toRemove);
		}
	}

	public static class LimitController
	{
		private long limit;
		private long recordsUsed = 0;

		public LimitController(long limit)
		{
			this.limit = limit;
		}

		public boolean addRecordIfPossible()
		{
			if(limit > recordsUsed)
			{
				++recordsUsed;
				return true;
			}
			return false;
		}

		public void removeRecord()
		{
			--recordsUsed;
		}

		public long getLimit()
		{
			return limit;
		}

		public long getRecordsUsed()
		{
			return recordsUsed;
		}
	}
}

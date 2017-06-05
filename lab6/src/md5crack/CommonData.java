package md5crack;

import java.nio.charset.Charset;

public interface CommonData
{
	Charset CHARSET = Charset.forName("UTF-8");
	byte[] ALPHABET = new String("ACGT").getBytes(CHARSET);
	int MAX_STRING_LENGTH = 15;

	String HASH_TYPE = "MD5";
	int HASH_LENGTH = 32;

	int CLIENT_RECONNECT_MAX = 5;
	long CLIENT_RECONNECT_PERIOD = 3000;
	int SIZEOF_LONG = 8;

	long NO_JOB_TO_DO = -1L;
	long HASH_NOT_CRACKED = -1L;
}

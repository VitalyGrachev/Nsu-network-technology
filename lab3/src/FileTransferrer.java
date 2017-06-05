public interface FileTransferrer
{
	int BUFSIZE = 4608;    //  4096 + 512

	byte STATUS_OK = 1;
	byte STATUS_FAIL = 2;

	byte MSG_TYPE_FILENAME = 0;
	byte MSG_TYPE_FILELEN = 1;
	byte MSG_TYPE_DATABLOCK = 2;
	byte MSG_TYPE_FILEEND = 3;
}

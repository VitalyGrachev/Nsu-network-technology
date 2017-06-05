public interface NodeConstants
{
	byte MSG_TYPE_ChildConnect = 0x01;
	byte MSG_TYPE_ChangeParent = 0x02;
	byte MSG_TYPE_GetChildren = 0x04;
	byte MSG_TYPE_TextMessage = 0x08;
	byte MSG_TYPE_Confirmation = 0x10;

	int SIZEOF_INT = 4;
	int SIZEOF_LONG = 8;
}

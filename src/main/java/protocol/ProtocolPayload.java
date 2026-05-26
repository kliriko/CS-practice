package protocol;

public class ProtocolPayload {
    public byte bMagic;
    public byte bSrc;
    public long bPktId;
    public int wLen;
    public int wCrc16Header;
    public int wCrc16Message;
    public ProtocolMessage message;
}

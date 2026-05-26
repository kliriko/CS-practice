package protocol;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class ProtocolMessage {
    public int cType;
    public int bUserId;
    public byte[] message;

    public byte[] decrypt(byte[] key) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        return cipher.doFinal(message);
    }

    public byte[] encrypt(byte[] key) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        return cipher.doFinal(message);
    }
}

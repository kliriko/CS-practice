package command;

public class CommandResponse {
    public final long packetId;
    public final int userId;
    public final boolean success;
    public final String message;
    public final int intData;

    private CommandResponse(long packetId, int userId, boolean success, String message, int intData) {
        this.packetId = packetId;
        this.userId = userId;
        this.success = success;
        this.message = message;
        this.intData = intData;
    }

    public static CommandResponse ok(long packetId, int userId) {
        return new CommandResponse(packetId, userId, true, "OK", 0);
    }

    public static CommandResponse ok(long packetId, int userId, int data) {
        return new CommandResponse(packetId, userId, true, "OK", data);
    }

    public static CommandResponse error(long packetId, int userId, String reason) {
        return new CommandResponse(packetId, userId, false, reason, 0);
    }

    public static CommandResponse of(long packetId, int userId, boolean success, String message, int intData) {
        return new CommandResponse(packetId, userId, success, message, intData);
    }
}

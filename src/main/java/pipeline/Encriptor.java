package pipeline;

import command.CommandResponse;

public interface Encriptor {
    byte[] encrypt(CommandResponse message);
}

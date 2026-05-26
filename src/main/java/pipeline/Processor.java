package pipeline;

import command.WarehouseCommand;

public interface Processor {
    void process(WarehouseCommand message);
}

package pipeline;

import command.CommandResponse;
import command.WarehouseCommand;
import warehouse.Warehouse;

import java.util.concurrent.BlockingQueue;

public class CommandProcessor implements Processor {
    private final BlockingQueue<CommandResponse> outputQueue;
    private final Warehouse warehouse;

    public CommandProcessor(BlockingQueue<CommandResponse> outputQueue, Warehouse warehouse) {
        this.outputQueue = outputQueue;
        this.warehouse = warehouse;
    }

    @Override
    public void process(WarehouseCommand message) {
        CommandResponse response = execute(message);
        try {
            outputQueue.put(response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private CommandResponse execute(WarehouseCommand cmd) {
        try {
            return switch (cmd.type) {
                case GET_STOCK -> {
                    int qty = warehouse.getStock(((WarehouseCommand.GetStock) cmd).productId);
                    yield CommandResponse.ok(cmd.packetId, cmd.userId, qty);
                }
                case DEBIT_STOCK -> {
                    WarehouseCommand.DebitStock c = (WarehouseCommand.DebitStock) cmd;
                    warehouse.debitStock(c.productId, c.quantity);
                    yield CommandResponse.ok(cmd.packetId, cmd.userId);
                }
                case CREDIT_STOCK -> {
                    WarehouseCommand.CreditStock c = (WarehouseCommand.CreditStock) cmd;
                    warehouse.creditStock(c.productId, c.quantity);
                    yield CommandResponse.ok(cmd.packetId, cmd.userId);
                }
                case ADD_GROUP -> {
                    int id = warehouse.addGroup(((WarehouseCommand.AddGroup) cmd).groupName);
                    yield CommandResponse.ok(cmd.packetId, cmd.userId, id);
                }
                case ADD_PRODUCT -> {
                    WarehouseCommand.AddProduct c = (WarehouseCommand.AddProduct) cmd;
                    int id = warehouse.addProduct(c.groupId, c.productName);
                    yield CommandResponse.ok(cmd.packetId, cmd.userId, id);
                }
                case SET_PRICE -> {
                    WarehouseCommand.SetPrice c = (WarehouseCommand.SetPrice) cmd;
                    warehouse.setPrice(c.productId, c.price);
                    yield CommandResponse.ok(cmd.packetId, cmd.userId);
                }
            };
        } catch (Exception e) {
            return CommandResponse.error(cmd.packetId, cmd.userId, e.getMessage());
        }
    }
}

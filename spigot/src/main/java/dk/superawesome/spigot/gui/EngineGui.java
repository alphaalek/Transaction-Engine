package dk.superawesome.spigot.gui;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dk.superawesome.core.EngineQuery;
import dk.superawesome.core.SingleTransactionNode;
import dk.superawesome.core.TransactionNode;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;

public class EngineGui<N extends TransactionNode> {

    private final Gui gui;
    private final QueryContext<N> context;
    private TransactionVisitor<N> visitor;

    private boolean hasDisplayedInitial;
    private int scrolledDown;

    private record QueryContext<CN extends TransactionNode>(QueryContext<CN> previousContext, EngineQuery<CN> query) {

    }

    public EngineGui(EngineQuery<N> query) {
        this(query, null);
    }

    private EngineGui(EngineQuery<N> query, QueryContext<N> previousContext) {
        this.context = new QueryContext<>(previousContext, query);
        this.gui = Gui.gui()
                .title(Component.text("Transaktioner (" + query.size() + ")"))
                .rows(6)
                .disableAllInteractions()
                .create();

        for (int i : Arrays.asList(8, 17, 26, 35, 47, 48, 49, 50, 51, 52)) {
            this.gui.setItem(i, new GuiItem(new ItemStack(Material.STAINED_GLASS_PANE, 1, (short) 15)));
        }

        if (previousContext != null) {
            this.gui.setItem(44, new GuiItem(Material.ARROW, event -> clickBack((Player) event.getWhoClicked())));
        }

        this.gui.setItem(45, new GuiItem(Material.ARROW, __ -> clickUp()));
        this.gui.setItem(53, new GuiItem(Material.ARROW, __ -> clickDown()));
        this.gui.setItem(46, new GuiItem(new ItemStack(Material.WOOL, 1, (short) 4), event -> clickNewSettings((Player) event.getWhoClicked())));

        displayNodes();
    }

    public void open(Player player) {
        this.gui.open(player);
    }

    @SuppressWarnings("unchecked")
    private TransactionVisitor<N> getVisitor(TransactionNode node) {
        if (this.visitor != null) {
            return this.visitor;
        }

        if (node.isGrouped()) {
            return this.visitor = (TransactionVisitor<N>) new GroupedTransactionVisitor();
        } else {
            return this.visitor = (TransactionVisitor<N>) new SingleTransactionVisitor();
        }
    }

    private void displayNodes() {
        if (hasDisplayedInitial && !this.context.query().isEmpty()) {
            // clear previous items
            for (int i = 0; i < 7; i++) {
                for (int j = 0; j < 4; j++) {
                    this.gui.setItem(i, j, new GuiItem(Material.AIR));
                }
            }
        }

        int i = 0, c = 0;
        for (N node : this.context.query().nodes()) {
            if (c >= scrolledDown) {
                TransactionVisitor<N> visitor = getVisitor(node);

                ItemStack item = new ItemStack(Material.SKULL_ITEM);
                visitor.applyToItem(node, item);

                int slot = (c - scrolledDown) * 9 + i;
                this.gui.setItem(slot, new GuiItem(item));
            }

            i++;
            if (i > 8) {
                c++;
                i = 0;
            }

            if (c - scrolledDown > 4) {
                break;
            }
        }

        hasDisplayedInitial = true;
    }

    private interface TransactionVisitor<T extends TransactionNode> {

        void applyToItem(T node, ItemStack item);
    }

    private static class SingleTransactionVisitor implements TransactionVisitor<SingleTransactionNode>  {

        @Override
        public void applyToItem(SingleTransactionNode node, ItemStack item) {
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            meta.setOwner(node.toUserName());
            meta.setDisplayName("§e" + node.fromUserName() + "§7 -> §e" + node.toUserName());
            item.setItemMeta(meta);
        }
    }

    private static class GroupedTransactionVisitor implements TransactionVisitor<TransactionNode.GroupedTransactionNode> {

        @Override
        public void applyToItem(TransactionNode.GroupedTransactionNode node, ItemStack item) {
            SkullMeta meta = (SkullMeta) item.getItemMeta();

            switch (node.bound()) {
                case TO:
                    String toPlayer = node.nodes().stream().map(SingleTransactionNode::toUserName).findFirst().orElseThrow();

                    meta.setOwner(toPlayer);
                    meta.setDisplayName("§7Til §e" + toPlayer);
                    break;
                case FROM:
                    String fromPlayer = node.nodes().stream().map(SingleTransactionNode::fromUserName).findFirst().orElseThrow();

                    meta.setOwner(fromPlayer);
                    meta.setDisplayName("§7Fra §e" + fromPlayer);
                    break;
            }

            item.setItemMeta(meta);
        }
    }

    private void clickNewSettings(Player player) {
        new EngineSettingsGui().open(player);
    }

    private void clickBack(Player player) {
        new EngineGui<>(this.context.previousContext().query(), this.context.previousContext().previousContext())
                .open(player);
    }

    private void clickUp() {
        if (scrolledDown == 0) {
            return;
        }

        scrolledDown--;
        displayNodes();
    }

    private void clickDown() {
        if (this.context.query().size() - scrolledDown * 8 < 5 * 8) {
            return;
        }

        scrolledDown++;
        displayNodes();
    }
}

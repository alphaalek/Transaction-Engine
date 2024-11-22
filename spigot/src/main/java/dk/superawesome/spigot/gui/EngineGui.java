package dk.superawesome.spigot.gui;

import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dk.superawesome.core.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.SkullType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;

public class EngineGui<N extends TransactionNode> {

    private static final DecimalFormat EMERALD_FORMATTER = new DecimalFormat("#,###.##", new DecimalFormatSymbols(Locale.GERMANY));
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final Gui gui;
    private final QueryContext<N, ? extends TransactionNode> context;
    private final EngineSettingsGui settings;
    private TransactionVisitor<N> visitor;

    private boolean hasDisplayedInitial;
    private int scrolledDown;

    private record QueryContext<CN extends TransactionNode, FN extends TransactionNode>(QueryContext<FN, ? extends TransactionNode> previousContext, EngineQuery<CN> query) {

    }

    public EngineGui(EngineQuery<N> query, EngineSettingsGui settings) {
        this(query, null, settings);
    }

    private EngineGui(EngineQuery<N> query, QueryContext<?, ?> previousContext, EngineSettingsGui settings) {
        this.context = new QueryContext<>(previousContext, query);
        this.settings = settings;
        this.gui = Gui.gui()
                .title(Component.text("Transaktioner (" + query.size() + ")"))
                .rows(6)
                .disableAllInteractions()
                .create();

        for (int i : Arrays.asList(8, 17, 26, 35, 47, 48, 49, 50, 51, 52)) {
            this.gui.setItem(i, new GuiItem(new ItemStack(Material.STAINED_GLASS_PANE, 1, (short) 15)));
        }

        if (previousContext != null) {
            this.gui.setItem(45, new GuiItem(
                    ItemBuilder.from(Material.ARROW)
                            .name(Component.text("§6Gå tilbage"))
                            .build(), event -> clickBack((Player) event.getWhoClicked())));
        }
        this.gui.setItem(44, new GuiItem(
                ItemBuilder.from(Material.ARROW)
                        .name(Component.text("§6Rul opad"))
                        .build(), __ -> clickUp()));

        this.gui.setItem(53, new GuiItem(
                ItemBuilder.from(Material.ARROW)
                        .name(Component.text("§6Rul nedad"))
                        .build(), __ -> clickDown()));

        this.gui.setItem(46, new GuiItem(
                ItemBuilder.from(new ItemStack(Material.WOOL, 1, (short) 14))
                        .name(Component.text("§cTilbage til indstillinger"))
                        .build(), event -> clickNewSettings((Player) event.getWhoClicked())));

        displayNodes();
    }

    public void open(Player player) {
        if (!this.context.query().isEmpty()) {
            this.gui.open(player);
        } else {
            player.sendMessage("§cDenne transaktionsforespørgsel er tom!");
        }
    }

    @SuppressWarnings("unchecked")
    private TransactionVisitor<N> getVisitor(TransactionNode node) {
        if (this.visitor != null) {
            return this.visitor;
        }

        if (node.isGrouped()) {
            return this.visitor = (TransactionVisitor<N>) new GroupedTransactionVisitor((QueryContext<TransactionNode.GroupedTransactionNode, SingleTransactionNode>) this.context, this.settings);
        } else {
            return this.visitor = (TransactionVisitor<N>) new SingleTransactionVisitor((QueryContext<SingleTransactionNode, ?>) this.context, this.settings);
        }
    }

    private void displayNodes() {
        if (hasDisplayedInitial && !this.context.query().isEmpty()) {
            // clear previous items
            for (int i = 1; i < 9; i++) {
                for (int j = 1; j < 6; j++) {
                    this.gui.setItem(j, i, new GuiItem(Material.AIR));
                }
            }
        }

        int i = 0, c = 0, f = 0;
        for (N node : this.context.query().nodes()) {
            if (c >= scrolledDown) {
                TransactionVisitor<N> visitor = getVisitor(node);

                ItemStack item = new ItemStack(Material.SKULL_ITEM, 1, (short) 0, (byte) SkullType.PLAYER.ordinal());
                visitor.applyToItem(node, item, f);

                int slot = (c - scrolledDown) * 9 + i;
                this.gui.setItem(slot, new GuiItem(item, event -> this.visitor.clickInspection((Player) event.getWhoClicked(), node)));
            }

            f++;
            i++;
            if (i > 7) {
                c++;
                i = 0;
            }

            if (c - scrolledDown > 4) {
                break;
            }
        }

        gui.update();
        hasDisplayedInitial = true;
    }

    private void clickNewSettings(Player player) {
        this.settings.open(player);
    }

    @SuppressWarnings("unchecked")
    private <CN extends TransactionNode> void clickBack(Player player) {
        new EngineGui<>((EngineQuery<CN>) this.context.previousContext().query(), (QueryContext<CN, N>) this.context.previousContext().previousContext(), this.settings)
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

    private interface TransactionVisitor<T extends TransactionNode> {

        void applyToItem(T node, ItemStack item, int index);

        void clickInspection(Player player, T node);
    }

    private record SingleTransactionVisitor(QueryContext<SingleTransactionNode, ?> context, EngineSettingsGui settings) implements TransactionVisitor<SingleTransactionNode>  {

        @Override
        public void applyToItem(SingleTransactionNode node, ItemStack item, int index) {
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            meta.setOwner(node.toUserName());
            meta.setDisplayName("§e" + node.fromUserName() + "§7 -> §e" + node.toUserName() + " §8(§e" + index + "§8) (Klik)");

            List<String> lore = new ArrayList<>();
            lore.add("§8Ved inspektion, ser du alle transaktioner");
            lore.add("§8denne spiller har overført efter datoen.");
            lore.add("");
            lore.add("§7Beløb: " + EMERALD_FORMATTER.format(node.amount()) + " emeralder");
            lore.add("§7Tidspunkt: " + TIME_FORMATTER.format(node.time()));
            lore.add("§7Transaktionstype: " + node.type().toString().toLowerCase());
            if (node.extra() != null) {
                lore.add("§7Ekstra: " + node.extra());
            }
            meta.setLore(lore);

            item.setItemMeta(meta);
        }

        @Override
        public void clickInspection(Player player, SingleTransactionNode node) {
            EngineQuery<SingleTransactionNode> newQuery = new EngineQuery<>(this.context.query(), false)
                    .filter(QueryFilter.FilterTypes.TIME.makeFilter(d -> d.isAfter(node.time())))
                    .filter(QueryFilter.FilterTypes.FROM_USER.makeFilter(p -> p.equalsIgnoreCase(node.toUserName())));

            new EngineGui<>(newQuery, this.context, this.settings)
                    .open(player);
        }
    }

    private record GroupedTransactionVisitor(QueryContext<TransactionNode.GroupedTransactionNode, SingleTransactionNode> context, EngineSettingsGui settings) implements TransactionVisitor<TransactionNode.GroupedTransactionNode> {

        @Override
        public void applyToItem(TransactionNode.GroupedTransactionNode node, ItemStack item, int index) {
            SkullMeta meta = (SkullMeta) item.getItemMeta();

            String other;
            Function<SingleTransactionNode, String> targetFunction;
            switch (node.bound()) {
                case TO:
                    targetFunction = SingleTransactionNode::fromUserName;
                    String toPlayer = node.nodes().stream().map(SingleTransactionNode::toUserName).findFirst().orElseThrow();

                    meta.setOwner(toPlayer);
                    other = "Fra";
                    meta.setDisplayName("§7Til §e" + toPlayer + " §8(§e" + index + "§8) (Klik for inspektion)");
                    break;
                case FROM:
                    targetFunction = SingleTransactionNode::toUserName;
                    String fromPlayer = node.nodes().stream().map(SingleTransactionNode::fromUserName).findFirst().orElseThrow();

                    meta.setOwner(fromPlayer);
                    other = "Til";
                    meta.setDisplayName("§7Fra §e" + fromPlayer + " §8(§e" + index + "§8) (Klik for inspektion)");
                    break;
                default:
                    throw new IllegalStateException();
            }

            List<String> lore = new ArrayList<>();
            lore.add("§7" + EMERALD_FORMATTER.format(node.getAmount()) + " emeralder i alt");
            lore.add("§7" + node.size() + " transaktioner i alt");

            Optional<SingleTransactionNode> highestOptional = node.getHighestTransaction();
            if (highestOptional.isPresent()) {
                SingleTransactionNode highest = highestOptional.get();
                lore.add("");
                lore.add("§8Højeste transaktion:");
                lore.add("§7" + other + " " + targetFunction.apply(highest) + ": " + EMERALD_FORMATTER.format(highest.amount()) + " emeralder");
                lore.add("§7Overført d. " + TIME_FORMATTER.format(highest.time()));
            }

            Optional<SingleTransactionNode> latestOptional = node.getLatestTransaction();
            if (latestOptional.isPresent()) {
                SingleTransactionNode latest = latestOptional.get();
                lore.add("");
                lore.add("§8Seneste transaktion:");
                lore.add("§7" + other + " " + targetFunction.apply(latest) + ": " + EMERALD_FORMATTER.format(latest.amount()) + " emeralder");
                lore.add("§7Overført d. " + TIME_FORMATTER.format(latest.time()));
            }

            Optional<SingleTransactionNode> oldestOptional = node.getOldestTransaction();
            if (oldestOptional.isPresent()) {
                SingleTransactionNode oldest = oldestOptional.get();
                lore.add("");
                lore.add("§8Ældste transaktion:");
                lore.add("§7" + other + " " + targetFunction.apply(oldest) + ": " + EMERALD_FORMATTER.format(oldest.amount()) + " emeralder");
                lore.add("§7Overført d. " + TIME_FORMATTER.format(oldest.time()));
            }

            meta.setLore(lore);

            item.setItemMeta(meta);
        }

        @Override
        public void clickInspection(Player player, TransactionNode.GroupedTransactionNode node) {
            EngineQuery<SingleTransactionNode> newQuery = new EngineQuery<>(node.nodes(), this.context.query().initialNodes());

            new EngineGui<>(
                    new EngineQuery<>(
                            Engine.doTransformation(Node.Collection.SINGLE, this.settings.getSortingMethod(), newQuery), true
                    ),
                    this.context,
                    this.settings
            )
            .open(player);
        }
    }
}

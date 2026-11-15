package com.otectus.immersivetanning.client.station;

import com.otectus.immersivetanning.client.screen.VanillaGui;
import com.otectus.immersivetanning.config.ClientConfig;
import com.otectus.immersivetanning.leather.Finish;
import com.otectus.immersivetanning.network.packet.BenchOffersPacket;
import com.otectus.immersivetanning.registry.ModSounds;
import com.otectus.immersivetanning.util.Scores;
import com.otectus.immersivetanning.work.WorkAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The pattern book: a small panel at the side of the bench view, never over the work. Patterns the player can make
 * come first; search, category tabs, favourites and "repeat last" are at hand. Choosing a pattern shows what it makes,
 * what it costs, which leather group it would be cut from (best Integrity first, shown) and any progression refusal -
 * before anything is taken. "Prepare" only lays the pattern and materials on the bench; nothing is made yet.
 */
final class PatternBook {
    private static final String[] TABS = {"all", "armor", "tack", "other", "favorites"};

    @Nullable
    private BenchOffersPacket offers;
    private int tab;
    private int scroll;
    @Nullable
    private ResourceLocation selected;
    private int group;
    private EditBox search;
    private final List<Hud.Button> buttons = new ArrayList<>();
    private final List<Object[]> rows = new ArrayList<>();
    private int x;
    private int y;
    private int w;
    private int h;
    private int listTop;
    private int listRows;
    /** Labels for results that share a name with another (see {@link VariantLabels}), recomputed with each offer. */
    private java.util.Map<net.minecraft.world.item.Item, Component> variants = java.util.Map.of();

    void offers(BenchOffersPacket packet) {
        boolean first = offers == null;
        offers = packet;
        variants = VariantLabels.of(packet.entries().stream().map(BenchOffersPacket.Entry::result).toList());
        if (first && packet.lastRecipe() != null) selected = packet.lastRecipe();
        if (first) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.BENCH_PAGE.get(), 1.0F, 0.6F));
    }

    List<Hud.Button> buttons() {
        return List.copyOf(buttons);
    }

    boolean loaded() {
        return offers != null;
    }

    private List<BenchOffersPacket.Entry> visible() {
        if (offers == null) return List.of();
        String query = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        List<String> favorites = ClientConfig.favorites();
        List<BenchOffersPacket.Entry> craftable = new ArrayList<>();
        List<BenchOffersPacket.Entry> other = new ArrayList<>();
        for (BenchOffersPacket.Entry e : offers.entries()) {
            boolean fav = favorites.contains(e.recipe().toString());
            if (tab == 4 && !fav) continue;
            if (tab >= 1 && tab <= 3 && e.category() != tab - 1) continue;
            if (!query.isEmpty() && !named(e.result()).getString().toLowerCase(Locale.ROOT).contains(query)
                    && !e.recipe().toString().contains(query)) continue;
            (e.craftable() ? craftable : other).add(e);
        }
        craftable.addAll(other);
        return craftable;
    }

    @Nullable
    private BenchOffersPacket.Entry entry(@Nullable ResourceLocation id) {
        if (offers == null || id == null) return null;
        for (BenchOffersPacket.Entry e : offers.entries()) if (e.recipe().equals(id)) return e;
        return null;
    }

    boolean covers(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    void render(GuiGraphics g, Font font, int width, int height, int mx, int my) {
        buttons.clear();
        rows.clear();
        w = Math.min(250, Math.max(170, width * 2 / 5));
        h = Math.max(150, height - 74);
        x = 6;
        y = 6;
        VanillaGui.panel(g, x, y, w, h);
        g.drawString(font, Component.translatable("book.immersive_tanning.title"), x + 7, y + 6, VanillaGui.LABEL, false);
        if (search == null) {
            search = new EditBox(font, x + 7, y + 18, w - 14, 14, Component.translatable("book.immersive_tanning.search"));
            search.setHint(Component.translatable("book.immersive_tanning.search"));
            search.setMaxLength(40);
        }
        search.setX(x + 7);
        search.setY(y + 18);
        search.setWidth(w - 14);
        search.render(g, mx, my, 0F);
        int tx = x + 7;
        for (int i = 0; i < TABS.length; i++) {
            Component label = Component.translatable("book.immersive_tanning.tab." + TABS[i]);
            Hud.Button b = Hud.button(g, font, "tab" + i, label, tx, y + 36, true, i == tab);
            buttons.add(b);
            tx += b.w() + 2;
        }
        if (offers == null) {
            VanillaGui.centered(g, font, Component.translatable("book.immersive_tanning.loading"), x + w / 2, y + 70, VanillaGui.LABEL_DIM);
            return;
        }
        List<BenchOffersPacket.Entry> list = visible();
        int detailsH = 92;
        listTop = y + 54;
        int listH = h - (listTop - y) - detailsH - 6;
        listRows = Math.max(1, listH / 18);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - listRows)));
        VanillaGui.inset(g, x + 6, listTop - 1, w - 12, listRows * 18 + 2);
        for (int i = 0; i < listRows && i + scroll < list.size(); i++) {
            BenchOffersPacket.Entry e = list.get(i + scroll);
            int ry = listTop + i * 18;
            boolean hover = mx >= x + 7 && mx < x + w - 8 && my >= ry && my < ry + 18;
            boolean sel = e.recipe().equals(selected);
            VanillaGui.row(g, x + 7, ry, w - 14, 18, e.craftable(), sel || hover);
            g.renderItem(e.result(), x + 9, ry + 1);
            Component name = named(e.result());
            g.drawString(font, VanillaGui.clipped(font, name, w - 64), x + 28, ry + 5, e.craftable() ? VanillaGui.ROW_TEXT : VanillaGui.ROW_TEXT_DISABLED, false);
            boolean fav = ClientConfig.favorites().contains(e.recipe().toString());
            g.drawString(font, fav ? "★" : "", x + w - 20, ry + 5, 0xFFFFD66B, false);
            rows.add(new Object[]{e.recipe(), ry});
        }
        if (list.isEmpty()) VanillaGui.centered(g, font, Component.translatable("book.immersive_tanning.empty"), x + w / 2, listTop + 8, VanillaGui.LABEL_DIM);
        details(g, font, mx, my, x + 6, listTop + listRows * 18 + 6, w - 12, detailsH);
    }

    private void details(GuiGraphics g, Font font, int mx, int my, int dx, int dy, int dw, int dh) {
        BenchOffersPacket.Entry e = entry(selected);
        VanillaGui.display(g, dx, dy, dw, dh);
        if (e == null) {
            VanillaGui.wrapped(g, font, Component.translatable("book.immersive_tanning.choose"), dx + 4, dy + 4, dw - 8, VanillaGui.DISPLAY_TEXT_DIM, 3);
            return;
        }
        g.renderItem(e.result(), dx + 3, dy + 3);
        g.drawString(font, VanillaGui.clipped(font, named(e.result()), dw - 26), dx + 22, dy + 3, VanillaGui.DISPLAY_HEADING, false);
        g.drawString(font, Component.translatable("book.immersive_tanning.cost", e.leather(), e.cuts(), e.panels(), e.stitches()),
                dx + 22, dy + 13, VanillaGui.DISPLAY_TEXT_DIM, false);
        int ax = dx + 3;
        for (ItemStack aux : e.auxiliary()) {
            g.renderItem(aux, ax, dy + 24);
            g.renderItemDecorations(font, aux, ax, dy + 24);
            ax += 18;
        }
        if (e.auxMissing()) g.drawString(font, Component.translatable("book.immersive_tanning.missing_aux"), ax + 2, dy + 28, 0xFFFF8080, false);
        int line = dy + 44;
        if (e.denial() != null) {
            VanillaGui.wrapped(g, font, e.denial(), dx + 4, line, dw - 8, 0xFFFF8080, 2);
        } else if (e.groups().isEmpty()) {
            g.drawString(font, Component.translatable("book.immersive_tanning.no_group", e.leather()), dx + 4, line, 0xFFFF8080, false);
        } else {
            group = Math.min(group, e.groups().size() - 1);
            int gx = dx + 4;
            for (int i = 0; i < e.groups().size() && gx < dx + dw - 30; i++) {
                BenchOffersPacket.Group gr = e.groups().get(i);
                MutableComponent label = gr.finish().displayName().copy();
                if (gr.trait() != null) label = label.append(" ").append(gr.trait().displayName());
                Hud.Button b = Hud.button(g, font, "group" + i, label, gx, line, true, i == group);
                buttons.add(b);
                gx += b.w() + 2;
            }
            BenchOffersPacket.Group gr = e.groups().get(group);
            g.drawString(font, Component.translatable("book.immersive_tanning.best_first", gr.units(), Scores.label(gr.integrity())),
                    dx + 4, line + 17, VanillaGui.DISPLAY_TEXT_DIM, false);
        }
        int by = dy + dh - 17;
        Hud.Button prepare = Hud.button(g, font, "prepare", Component.translatable("book.immersive_tanning.prepare"), dx + 4, by, e.craftable(), false);
        buttons.add(prepare);
        boolean fav = ClientConfig.favorites().contains(e.recipe().toString());
        buttons.add(Hud.button(g, font, "favorite", Component.literal(fav ? "★" : "☆"), prepare.x() + prepare.w() + 3, by, true, false));
        int right = dx + dw - 4;
        if (offers.lastRecipe() != null) {
            Component label = Component.translatable("book.immersive_tanning.repeat");
            right -= font.width(label) + 10;
            buttons.add(Hud.button(g, font, "repeat", label, right, by, true, false));
        }
        if (com.otectus.immersivetanning.client.compat.RecipeViewer.available()) {
            Component label = Component.translatable("book.immersive_tanning.viewer");
            right -= font.width(label) + 13;
            buttons.add(Hud.button(g, font, "viewer", label, right, by, true, false));
        }
    }

    /** The result's name, with its variant label when another result shares the name. */
    private Component named(ItemStack result) {
        Component label = variants.get(result.getItem());
        if (label == null) return result.getHoverName();
        return Component.translatable("book.immersive_tanning.variant", result.getHoverName(), label);
    }

    /** The label this book shows for a result, or null when its name is its own. */
    @Nullable
    Component variantLabel(ItemStack result) {
        return variants.get(result.getItem());
    }

    /** Returns whether the click was in the book. */
    boolean click(double mx, double my) {
        if (!covers(mx, my)) return false;
        if (search != null && search.mouseClicked(mx, my, 0)) {
            search.setFocused(true);
            return true;
        }
        if (search != null) search.setFocused(false);
        for (Hud.Button b : buttons) {
            if (!b.contains(mx, my) || !b.enabled()) continue;
            String id = b.id();
            if (id.startsWith("tab")) {
                tab = Integer.parseInt(id.substring(3));
                scroll = 0;
            } else if (id.startsWith("group")) {
                group = Integer.parseInt(id.substring(5));
            } else if (id.equals("prepare")) {
                prepare();
            } else if (id.equals("favorite")) {
                toggleFavorite();
            } else if (id.equals("repeat")) {
                StationFocus.send(WorkAction.BENCH_AGAIN);
            } else if (id.equals("viewer")) {
                BenchOffersPacket.Entry e = entry(selected);
                if (e != null) com.otectus.immersivetanning.client.compat.RecipeViewer.show(e.result());
            }
            return true;
        }
        for (Object[] row : rows) {
            int ry = (int) row[1];
            if (my >= ry && my < ry + 18 && mx >= x + 7 && mx < x + w - 8) {
                ResourceLocation id = (ResourceLocation) row[0];
                if (mx >= x + w - 24) {
                    selected = id;
                    toggleFavorite();
                } else if (id.equals(selected)) {
                    prepare();
                } else {
                    selected = id;
                    group = 0;
                    BenchOffersPacket.Entry e = entry(id);
                    if (e != null && offers.lastFinish() != null) {
                        for (int i = 0; i < e.groups().size(); i++) {
                            BenchOffersPacket.Group gr = e.groups().get(i);
                            boolean sameTrait = gr.trait() == null ? offers.lastTrait() == null : gr.trait().id().equals(offers.lastTrait());
                            if (gr.finish() == offers.lastFinish() && sameTrait) group = i;
                        }
                    }
                }
                return true;
            }
        }
        return true;
    }

    private void prepare() {
        BenchOffersPacket.Entry e = entry(selected);
        if (e == null || !e.craftable()) return;
        BenchOffersPacket.Group gr = e.groups().get(Math.min(group, e.groups().size() - 1));
        StationFocus.send(WorkAction.BENCH_PREPARE, 0, new int[]{gr.finish().ordinal()}, new short[0], new short[0], e.recipe(),
                gr.trait() == null ? null : gr.trait().id());
    }

    private void toggleFavorite() {
        if (selected == null) return;
        List<String> favorites = new ArrayList<>(ClientConfig.favorites());
        String id = selected.toString();
        if (!favorites.remove(id)) favorites.add(id);
        ClientConfig.FAVORITE_PATTERNS.set(favorites);
        ClientConfig.SPEC.save();
    }

    boolean scroll(double mx, double my, double delta) {
        if (!covers(mx, my)) return false;
        scroll = Math.max(0, scroll - (int) Math.signum(delta));
        return true;
    }

    boolean key(int key, int scan, int modifiers) {
        if (search != null && search.isFocused()) return search.keyPressed(key, scan, modifiers) || key != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
        // Keyboard: up and down move through the patterns, Enter prepares the chosen one.
        List<BenchOffersPacket.Entry> list = visible();
        if (list.isEmpty()) return false;
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN || key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) {
            int at = 0;
            for (int i = 0; i < list.size(); i++) if (list.get(i).recipe().equals(selected)) at = i;
            at = Math.floorMod(at + (key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN ? 1 : -1), list.size());
            selected = list.get(at).recipe();
            group = 0;
            if (at < scroll) scroll = at;
            if (at >= scroll + listRows) scroll = at - listRows + 1;
            return true;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            prepare();
            return true;
        }
        return false;
    }

    /** Chooses a pattern (and the leather group by finish, when given) as a click on its row would. */
    void select(ResourceLocation recipe, @Nullable Finish finish) {
        selected = recipe;
        group = 0;
        BenchOffersPacket.Entry e = entry(recipe);
        if (e != null && finish != null) {
            for (int i = 0; i < e.groups().size(); i++) if (e.groups().get(i).finish() == finish) group = i;
        }
    }

    /** "Prepare" for the chosen pattern. */
    void prepareSelected() {
        prepare();
    }

    /** Whether a pattern is offered and makeable. */
    boolean makeable(ResourceLocation recipe) {
        BenchOffersPacket.Entry e = entry(recipe);
        return e != null && e.craftable();
    }

    boolean character(char c, int modifiers) {
        return search != null && search.isFocused() && search.charTyped(c, modifiers);
    }

    @Nullable
    static Finish finishOf(BenchOffersPacket.Group group) {
        return group.finish();
    }
}

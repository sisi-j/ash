package com.ashlauncher.client.v1_21_11;

import com.ashlauncher.client.settings.SettingsMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * ash's settings screen on 1.21.11: the shared {@link SettingsMenu}'s rows,
 * drawn as the game's own buttons. Every decision - what each row says,
 * whether it can be pressed, what pressing does, what the footer says - is
 * the menu's.
 *
 * <p>Laid out the same as 1.8.9's: a column of 200-wide buttons a quarter of
 * the way down, the title above, Done below, the footer beneath that.
 */
final class AshSettingsScreen extends Screen {

    private static final int BUTTON_WIDTH = 200;
    private static final int ROW_HEIGHT = 24;

    private final SettingsMenu menu;
    private final KeyMapping key;
    private final List<Button> rowButtons = new ArrayList<>();

    AshSettingsScreen(SettingsMenu menu, KeyMapping key) {
        super(Component.literal(SettingsMenu.TITLE));
        this.menu = menu;
        this.key = key;
    }

    @Override
    protected void init() {
        rowButtons.clear();
        int x = width / 2 - BUTTON_WIDTH / 2;
        int y = height / 4;
        for (SettingsMenu.Row row : menu.rows()) {
            Button button = Button.builder(Component.literal(row.label()), pressed -> {
                row.press();
                relabel();
            }).bounds(x, y, BUTTON_WIDTH, 20).build();
            button.active = row.available();
            rowButtons.add(addRenderableWidget(button));
            y += ROW_HEIGHT;
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, pressed -> onClose())
                .bounds(x, y + ROW_HEIGHT / 2, BUTTON_WIDTH, 20).build());
    }

    private void relabel() {
        for (int i = 0; i < rowButtons.size(); i++) {
            rowButtons.get(i).setMessage(Component.literal(menu.rows().get(i).label()));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 4 - 20, 0xFFFFFFFF);
        String footer = menu.footer();
        if (!footer.isEmpty()) {
            int top = height / 4 + (menu.rows().size() + 2) * ROW_HEIGHT;
            graphics.drawWordWrap(font, Component.literal(footer), width / 2 - BUTTON_WIDTH, top, BUTTON_WIDTH * 2,
                    0xFFFFFFFF);
        }
    }

    /**
     * A light dim rather than the game's blur, so the HUD stays readable
     * behind the screen and a switch can be seen taking effect - the same dim
     * 1.8.9's screen draws.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x60000000);
    }

    /**
     * The key that opened it closes it. A key binding gets no presses while a
     * screen is open - the screen is asked first - so the screen has to know
     * its own key.
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (key.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}

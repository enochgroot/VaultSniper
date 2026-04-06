package com.vaultsniper.screen;

import com.vaultsniper.VaultSniper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class VaultSniperScreen extends Screen {
    private EditBox addBox;
    private Button toggleBtn, addBtn, clearBtn;

    public VaultSniperScreen() { super(Component.literal("VaultSniper Config")); }

    @Override protected void init() {
        int cx = width / 2, cy = height / 2;

        // Toggle button
        toggleBtn = Button.builder(toggleLabel(),
            btn -> { VaultSniper.getInstance().toggle(); btn.setMessage(Component.literal(toggleLabel())); })
            .bounds(cx - 100, cy - 70, 200, 20).build();
        addRenderableWidget(toggleBtn);

        // Item input
        addBox = new EditBox(font, cx - 100, cy - 40, 155, 20, Component.literal("item id"));
        addBox.setMaxLength(100);
        addBox.setHint(Component.literal("e.g. heavy_core  or  minecraft:wind_charge"));
        addWidget(addBox);

        addBtn = Button.builder(Component.literal("Add Target"),
            btn -> {
                String v = addBox.getValue().trim();
                if (!v.isEmpty()) { VaultSniper.getInstance().addTarget(v); addBox.setValue(""); }
            }).bounds(cx + 60, cy - 40, 40, 20).build();
        addRenderableWidget(addBtn);

        clearBtn = Button.builder(Component.literal("Clear All"),
            btn -> VaultSniper.getInstance().clearTargets())
            .bounds(cx - 100, cy + 80, 95, 20).build();
        addRenderableWidget(clearBtn);

        addRenderableWidget(Button.builder(Component.literal("Close"),
            btn -> onClose()).bounds(cx + 5, cy + 80, 95, 20).build());
    }

    @Override public void render(GuiGraphics g, int mx, int my, float delta) {
        g.fillGradient(0, 0, width, height, 0xC0101018, 0xC0182030);
        int cx = width / 2, cy = height / 2;

        g.drawCenteredString(font, "§6§lVaultSniper", cx, cy - 95, 0xFFFFAA00);
        g.drawCenteredString(font, "Aim at a vault — auto-clicks when target item appears", cx, cy - 80, 0xFF888888);

        g.drawString(font, "Target items (click to remove):", cx - 100, cy - 10, 0xFFCCCCCC);

        // Draw target list
        List<String> targets = VaultSniper.getInstance().getTargets();
        if (targets.isEmpty()) {
            g.drawString(font, "  (none — add items above)", cx - 100, cy + 5, 0xFF555555);
        } else {
            int y = cy + 5;
            for (String t : targets) {
                boolean hovered = mx >= cx - 100 && mx <= cx + 100 && my >= y - 1 && my <= y + 9;
                g.fill(cx - 100, y - 1, cx + 100, y + 9, hovered ? 0x44FF4444 : 0x22FFFFFF);
                g.drawString(font, (hovered ? "§c✕ " : "§a✓ ") + t, cx - 95, y, 0xFFFFFFFF);
                y += 12;
            }
        }

        // Status
        String status = VaultSniper.getInstance().getStatus(Minecraft.getInstance());
        int col = status.contains("TARGET") ? 0xFF55FF55 : status.startsWith("Watching") ? 0xFF88CCFF : status.equals("OFF") ? 0xFF888888 : 0xFFFFDD44;
        g.drawCenteredString(font, "Status: " + status, cx, cy + 68, col);

        addBox.render(g, mx, my, delta);
        super.render(g, mx, my, delta);
    }

    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent ctx, boolean dbl) {
        if (ctx.button() == 0) {
            int cx = width / 2, cy = height / 2;
            List<String> targets = VaultSniper.getInstance().getTargets();
            int y = cy + 5;
            for (String t : targets) {
                if (ctx.x() >= cx - 100 && ctx.x() <= cx + 100 && ctx.y() >= y - 1 && ctx.y() <= y + 9) {
                    VaultSniper.getInstance().removeTarget(t);
                    return true;
                }
                y += 12;
            }
        }
        return super.mouseClicked(ctx, dbl);
    }

    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent ctx) {
        if (ctx.key() == 257 && addBox.isFocused()) { // Enter key
            String v = addBox.getValue().trim();
            if (!v.isEmpty()) { VaultSniper.getInstance().addTarget(v); addBox.setValue(""); }
            return true;
        }
        return super.keyPressed(ctx);
    }

    @Override public boolean isPauseScreen() { return false; }

    private String toggleLabel() {
        return VaultSniper.getInstance().isActive() ? "§a● SNIPER ACTIVE (click to disable)" : "§c○ SNIPER OFF (click to enable)";
    }
}

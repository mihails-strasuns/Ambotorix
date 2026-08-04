package vitbuk.com.Ambotorix;

import vitbuk.com.Ambotorix.entities.Leader;
import vitbuk.com.Ambotorix.entities.Player;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a player's leader pick pool as an image. One image can hold several players — one row each,
 * with the player's name at the row start, a portrait + wrapped name per leader, and a thin separator
 * between rows. The same renderer serves the public open-draft post (all players) and the per-player
 * DM pools (a single row).
 *
 * <p>Produces raw PNG bytes and knows nothing about how they are delivered — the chat adapter wraps
 * them in whatever its platform needs (a Telegram {@code SendPhoto}, a Discord {@code FileUpload}).
 */
public class PickImageGenerator {

    // Layout constants (px).
    private static final int ICON_SIZE = 56;        // a bit smaller than before
    private static final int ICON_GAP = 12;         // horizontal space between portraits
    private static final int NAME_COL_WIDTH = 120;  // left column holding the player's name
    private static final int LEFT_PAD = 12;
    private static final int RIGHT_PAD = 12;
    private static final int ROW_VPAD = 12;         // vertical padding inside each row
    private static final int LEADER_FONT_SIZE = 11;
    private static final int LEADER_TEXT_ROWS = 5;  // wrapped leader-name lines under each portrait
    private static final int LEADER_LINE_HEIGHT = 13;
    private static final int LEADER_TEXT_GAP = 6;   // gap between a portrait and its name
    private static final int LEADER_TEXT_WIDTH = ICON_SIZE + 8; // slightly wider than the icon

    // ---- Theme: simple but stylish, dark "gaming" palette. ----
    private static final int OUTER_PAD = 16;        // margin around the whole image
    private static final int PANEL_GAP = 12;        // vertical gap between player panels
    private static final int PANEL_RADIUS = 18;     // corner radius of each player's panel
    private static final int ACCENT_BAR_W = 5;      // colored bar on the panel's left edge
    private static final int RING = 2;              // accent ring thickness around portraits

    private static final Color BG_TOP = new Color(0x1B, 0x1F, 0x2A);      // gradient top
    private static final Color BG_BOTTOM = new Color(0x12, 0x15, 0x1D);   // gradient bottom
    private static final Color PANEL = new Color(0xFF, 0xFF, 0xFF, 0x0E); // translucent panel fill
    private static final Color PANEL_STROKE = new Color(0xFF, 0xFF, 0xFF, 0x14);
    private static final Color NAME_COLOR = new Color(0xF2, 0xF4, 0xF8);
    private static final Color LEADER_COLOR = new Color(0xAD, 0xB4, 0xC2);

    // Two rotating accent colors, alternated per row purely for visual rhythm
    // (no encoded meaning — just so adjacent panels read as distinct).
    private static final Color[] ACCENTS = {
            new Color(0x4C, 0x9A, 0xFF), // blue
            new Color(0x36, 0xC7, 0x9B), // teal
    };

    private static byte[] generatePickImage(List<Player> players) {
        int maxPicks = players.stream().mapToInt(p -> p.getPicks().size()).max().orElse(0);
        int rowContentHeight = ICON_SIZE + LEADER_TEXT_GAP + LEADER_TEXT_ROWS * LEADER_LINE_HEIGHT;
        int rowHeight = ROW_VPAD + rowContentHeight + ROW_VPAD;

        int width = LEFT_PAD + NAME_COL_WIDTH + maxPicks * (ICON_SIZE + ICON_GAP) + RIGHT_PAD;
        int height = Math.max(1, players.size()) * rowHeight;

        // Include outer margins and gaps between panels in the canvas size.
        int panelWidth = width;
        width = panelWidth + OUTER_PAD * 2;
        height = OUTER_PAD * 2 + players.size() * rowHeight
                + Math.max(0, players.size() - 1) * PANEL_GAP;

        BufferedImage finalImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = finalImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Backdrop: subtle vertical gradient.
        g.setPaint(new GradientPaint(0, 0, BG_TOP, 0, height, BG_BOTTOM));
        g.fillRect(0, 0, width, height);

        int rowTop = OUTER_PAD;
        int accentIdx = 0;
        for (Player player : players) {
            Color accent = ACCENTS[accentIdx++ % ACCENTS.length];

            // Rounded translucent panel for the whole row, with a left accent bar.
            RoundRectangle2D panel = new RoundRectangle2D.Float(
                    OUTER_PAD, rowTop, panelWidth, rowHeight, PANEL_RADIUS, PANEL_RADIUS);
            g.setColor(PANEL);
            g.fill(panel);
            Shape oldClip = g.getClip();
            g.setClip(panel);
            g.setColor(accent);
            g.fillRect(OUTER_PAD, rowTop, ACCENT_BAR_W, rowHeight);
            g.setClip(oldClip);
            g.setColor(PANEL_STROKE);
            g.setStroke(new BasicStroke(1f));
            g.draw(panel);

            // Player name in the left column, bold and vertically centred in the row.
            g.setFont(new Font("Verdana", Font.BOLD, 14));
            g.setColor(NAME_COLOR);
            int nameX = OUTER_PAD + LEFT_PAD + ACCENT_BAR_W;
            drawPlayerName(g, player.getUserName(), nameX, rowTop, NAME_COL_WIDTH - 8, rowHeight);

            int x = OUTER_PAD + LEFT_PAD + NAME_COL_WIDTH;
            int iconY = rowTop + ROW_VPAD;
            for (Leader leader : player.getPicks()) {
                BufferedImage leaderIcon;
                try {
                    leaderIcon = ImageIO.read(new File(leader.getPicPath()));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                drawCircularPortrait(g, leaderIcon, x, iconY, ICON_SIZE, accent);

                g.setFont(new Font("Verdana", Font.PLAIN, LEADER_FONT_SIZE));
                g.setColor(LEADER_COLOR);
                int textStartY = iconY + ICON_SIZE + LEADER_TEXT_GAP + g.getFontMetrics().getAscent();
                // Centre the (slightly wider) name block on the portrait's centre.
                int textX = x - (LEADER_TEXT_WIDTH - ICON_SIZE) / 2;
                drawWrappedText(g, leader.getFullName(), textX, textStartY, LEADER_TEXT_WIDTH, LEADER_TEXT_ROWS);

                x += ICON_SIZE + ICON_GAP;
            }
            rowTop += rowHeight + PANEL_GAP;
        }

        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(finalImage, "png", out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return out.toByteArray();
    }

    /** Draw a portrait clipped to a circle with a subtle accent ring. */
    private static void drawCircularPortrait(Graphics2D g, BufferedImage src, int x, int y, int size, Color accent) {
        Image scaled = src.getScaledInstance(size, size, Image.SCALE_SMOOTH);
        Shape oldClip = g.getClip();
        g.setClip(new Ellipse2D.Float(x, y, size, size));
        g.drawImage(scaled, x, y, null);
        g.setClip(oldClip);
        // Accent ring.
        g.setStroke(new BasicStroke(RING));
        g.setColor(accent);
        g.drawOval(x + RING / 2, y + RING / 2, size - RING, size - RING);
    }

    /** Draw the player's name centred in the left column, wrapped to a few lines, vertically centred. */
    private static void drawPlayerName(Graphics2D g, String name, int x, int rowTop, int maxWidth, int rowHeight) {
        FontMetrics fm = g.getFontMetrics();
        int lineHeight = fm.getHeight();
        List<String> lines = wrapLines(fm, name, maxWidth, 3);
        int blockHeight = lines.size() * lineHeight;
        int y = rowTop + (rowHeight - blockHeight) / 2 + fm.getAscent();
        for (String line : lines) {
            int textWidth = fm.stringWidth(line);
            g.drawString(line, x + (maxWidth - textWidth) / 2, y);
            y += lineHeight;
        }
    }

    private static List<String> wrapLines(FontMetrics fm, String text, int maxWidth, int maxRows) {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = current.length() == 0 ? word : current + " " + word;
            if (fm.stringWidth(test) > maxWidth && current.length() > 0) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(test);
            }
        }
        if (current.length() > 0) lines.add(current.toString());
        return lines.size() > maxRows ? new ArrayList<>(lines.subList(0, maxRows)) : lines;
    }

    private static void drawWrappedText(Graphics2D g, String text, int x, int y, int maxWidth, int maxRows) {
        FontMetrics metrics = g.getFontMetrics();
        int lineHeight = metrics.getHeight();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        int rowCount = 0;

        for (int i = 0; i < words.length; i++) {
            String word = words[i];

            if ((word.equalsIgnoreCase("of") || word.equalsIgnoreCase("de") || word.equalsIgnoreCase("the"))
                    && i + 1 < words.length) {
                word += " " + words[i + 1];
                i++;
            }

            String testLine = currentLine.toString() + " " + word;
            int testWidth = metrics.stringWidth(testLine.trim());

            if (testWidth > maxWidth && !currentLine.toString().isEmpty()) {
                drawCenteredString(g, currentLine.toString().trim(), x, y, maxWidth);
                y += lineHeight;
                rowCount++;

                currentLine = new StringBuilder(word);

                if (rowCount >= maxRows) {
                    break;
                }
            } else {
                currentLine.append(" ").append(word);
            }
        }

        if (!currentLine.toString().trim().isEmpty() && rowCount < maxRows) {
            drawCenteredString(g, currentLine.toString().trim(), x, y, maxWidth);
        }
    }

    private static void drawCenteredString(Graphics2D g, String text, int x, int y, int maxWidth) {
        FontMetrics metrics = g.getFontMetrics();
        int textWidth = metrics.stringWidth(text);
        int startX = x + (maxWidth - textWidth) / 2;
        g.drawString(text, startX, y);
    }

    /** A single player's pool (one row) — used for DMs. */
    public static byte[] renderPool(Player player) {
        return generatePickImage(List.of(player));
    }

    /** Every player's pool in one image (one row each) — used for the public open-draft post. */
    public static byte[] renderPools(List<Player> players) {
        return generatePickImage(players);
    }
}

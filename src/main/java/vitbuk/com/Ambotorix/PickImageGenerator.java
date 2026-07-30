package vitbuk.com.Ambotorix;

import vitbuk.com.Ambotorix.entities.Leader;
import vitbuk.com.Ambotorix.entities.Player;

import javax.imageio.ImageIO;
import java.awt.*;
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

    private static final Color BACKGROUND = Color.WHITE;
    private static final Color SEPARATOR = new Color(0xE0, 0xE0, 0xE0);
    private static final Color NAME_COLOR = new Color(0x22, 0x22, 0x22);

    private static byte[] generatePickImage(List<Player> players) {
        int maxPicks = players.stream().mapToInt(p -> p.getPicks().size()).max().orElse(0);
        int rowContentHeight = ICON_SIZE + LEADER_TEXT_GAP + LEADER_TEXT_ROWS * LEADER_LINE_HEIGHT;
        int rowHeight = ROW_VPAD + rowContentHeight + ROW_VPAD;

        int width = LEFT_PAD + NAME_COL_WIDTH + maxPicks * (ICON_SIZE + ICON_GAP) + RIGHT_PAD;
        int height = Math.max(1, players.size()) * rowHeight;

        BufferedImage finalImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = finalImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(BACKGROUND);
        g.fillRect(0, 0, width, height);

        int rowTop = 0;
        boolean first = true;
        for (Player player : players) {
            if (!first) {
                g.setColor(SEPARATOR);
                g.drawLine(LEFT_PAD, rowTop, width - RIGHT_PAD, rowTop);
            }
            first = false;

            // Player name in the left column, bold and vertically centred in the row.
            g.setFont(new Font("Verdana", Font.BOLD, 14));
            g.setColor(NAME_COLOR);
            drawPlayerName(g, player.getUserName(), LEFT_PAD, rowTop, NAME_COL_WIDTH - 8, rowHeight);

            int x = LEFT_PAD + NAME_COL_WIDTH;
            int iconY = rowTop + ROW_VPAD;
            for (Leader leader : player.getPicks()) {
                BufferedImage leaderIcon;
                try {
                    leaderIcon = ImageIO.read(new File(leader.getPicPath()));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                Image scaledIcon = leaderIcon.getScaledInstance(ICON_SIZE, ICON_SIZE, Image.SCALE_SMOOTH);
                g.drawImage(scaledIcon, x, iconY, null);

                g.setFont(new Font("Verdana", Font.PLAIN, LEADER_FONT_SIZE));
                g.setColor(Color.BLACK);
                int textStartY = iconY + ICON_SIZE + LEADER_TEXT_GAP + g.getFontMetrics().getAscent();
                // Centre the (slightly wider) name block on the portrait's centre.
                int textX = x - (LEADER_TEXT_WIDTH - ICON_SIZE) / 2;
                drawWrappedText(g, leader.getFullName(), textX, textStartY, LEADER_TEXT_WIDTH, LEADER_TEXT_ROWS);

                x += ICON_SIZE + ICON_GAP;
            }
            rowTop += rowHeight;
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

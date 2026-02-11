package golden.botc_mc.botc_mc.game;

import golden.botc_mc.botc_mc.botc;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ScriptBookGenerator {
    private final Script script;
    private final List<RawFilteredPair<Text>> pages = new ArrayList<>();
    private final Map<String, Integer> bookmarks = new HashMap<>();
    private int pageCount = 0;

    final private int MAX_PAGE_LINES = 14;
    final private int APPROX_PAGE_WIDTH = 16;
    final private int MAX_BREAKS = 2;

    public ScriptBookGenerator(Script script, Map<String, Integer> bookmarks) {
        this.script = script;
        if (bookmarks != null) {
            this.bookmarks.putAll(bookmarks);
        }
    }

    public Map<String, Integer> getBookmarks() {
        return bookmarks;
    }

//    private int countApproxLines(String text) {
//        if (text == null || text.isEmpty()) return 0;
//        int lines = 0;
//        String[] hardLines = text.split("\n", -1);
//        for (String hard : hardLines) {
//            if (hard.isEmpty()) { lines++; continue; }
//            int currLen = 0;
//            for (String word : hard.split("\\s+")) {
//                int w = word.length();
//                if (currLen == 0) {
//                    // start new line with this word (possibly longer than width)
//                    currLen = w;
//                } else if (currLen + 1 + w <= APPROX_PAGE_WIDTH) {
//                    currLen += 1 + w; // add space + word
//                } else {
//                    lines++;
//                    currLen = w;
//                }
//                // if a single word is longer than width, split it across multiple lines
//                if (currLen > APPROX_PAGE_WIDTH) {
//                    int extra = currLen / APPROX_PAGE_WIDTH;
//                    lines += extra;
//                    currLen = currLen % APPROX_PAGE_WIDTH;
//                }
//            }
//            if (currLen > 0) lines++;
//        }
//        return lines;
//    }

    private int countApproxLines(String text) {
        if (text == null || text.isEmpty()) return 0;
        return Arrays.stream(text.split("\n", -1))
                .mapToInt(line -> (int) Math.ceil((double) line.length() / APPROX_PAGE_WIDTH))
                .sum();
    }

    /**
     * Writes a section to the book pages, splitting it into multiple pages if necessary.
     * Keeps section title on each page.
     * @param sectionTitle The title of the section.
     * @param sectionContent The content of the section.
     */
    private void writeSection(Text sectionTitle, Map<String, ? extends Text> sectionContent, int maxBreaks) {
        List<Text> pageContent = new ArrayList<>();
        if (sectionTitle != null) {
            pageContent.add(sectionTitle);
        }
        int pageLength = sectionTitle != null ? countApproxLines(sectionTitle.getString()) : 0;

        for (String key : sectionContent.keySet()) {
            Text item = sectionContent.get(key);
            int itemLines = countApproxLines(item.getString());
            // If the item is too long to fit on the current page, write the current page and start a new one
            if (pageLength + itemLines > MAX_PAGE_LINES && pageContent.size() > 1) {
                writePage(pageContent, pageLength, maxBreaks, false);
                pageContent = new ArrayList<>();
                if (sectionTitle != null) {
                    pageContent.add(sectionTitle);
                }
                pageLength = sectionTitle != null ? countApproxLines(sectionTitle.getString()) : 0;
            }
            botc.LOGGER.info("Adding item {} to page {}", item, pageCount);
            pageContent.add(item);
            bookmarks.put(key, pageCount + 1); // +1 because pages are 1-indexed
            pageLength += itemLines;
        }
        if (!pageContent.isEmpty()) {
            writePage(pageContent, pageLength, maxBreaks, false);
        }
    }

    private void writeSection(Text sectionTitle, List<? extends Text> sectionContent) {
        Map<String, Text> contentMap = new LinkedHashMap<>();
        for (Text text : sectionContent) {
            contentMap.put(String.valueOf(text.hashCode()), text);
        }
        writeSection(sectionTitle, contentMap, MAX_BREAKS);
    }

    private void writeSection(Text sectionTitle, Map<String, ? extends Text> sectionContent) {
        writeSection(sectionTitle, sectionContent, MAX_BREAKS);
    }

    private void writePage(List<? extends Text> content, int pageLength, int maxBreaks, boolean padding) {
        MutableText page = Text.empty();
        int spareLines = MAX_PAGE_LINES - pageLength;
        int breaks = 0;
        if (spareLines > 0 && content.size() >= (padding ? 1 : 2)) {
            breaks = spareLines / (content.size() - (padding ? -1 : 1));
            breaks = Math.min(breaks, maxBreaks);
        }
        for (int i = 0; i < content.size(); i++) {
            Text text = content.get(i);
            if (breaks > 0 && i >= (padding ? 0 : 1)) {
                for (int b = 0; b < breaks; b++) {
                    page.append("\n");
                }
            }
            page.append(text);
            page.append("\n");
        }
        pages.add(RawFilteredPair.of(page));
        pageCount++;
    }

    private void writePage(List<? extends Text> content, int pageLength) {
        writePage(content, pageLength, 2, false);
    }

    public WrittenBookContentComponent generateWrittenBook() {
        addTitlePage();
        addCharacterListPages(List.of(Team.values()));
        addCharacterDetailPages();
        addBootleggerPages();
        addJinxesPages();
        addNightOrderPages("first", script.firstNightOrder());
        addNightOrderPages("other", script.otherNightOrder());
        addPlayerCountPage();
        return new WrittenBookContentComponent(
                RawFilteredPair.of(script.meta().name()),
                script.meta().author(),
                1,
                pages,
                false
                );
    }

    private void addTitlePage() {
        List<Text> pageContent = new ArrayList<>();
        pageContent.add(script.toFormattedText().styled(style -> style.withBold(true).withUnderline(true)));
        if (script.meta().author() != null && !script.meta().author().isEmpty()) {
            MutableText authorText = Text.literal("By ").append(script.meta().author());
            authorText.styled(style -> style.withBold(true));
            pageContent.add(authorText);
        }
        writePage(pageContent, 2, 3, true);
    }

    private void addCharacterListPages(List<Team> teams) {
        List<Text> pageContent = new ArrayList<>();

        for (Team team : teams) {
            List<Character> characters = this.script.getCharactersByTeam(team);
            MutableText header = team.toText();
            header.append(" - ");
            header.append(team.getDefaultAlignment().toText());
            header.styled(style -> style.withColor(team.getColour(true)).withBold(true).withUnderline(true));

            while (true) {
                // Write the alignment header
                // If there are no characters left, break the loop
                if (characters.isEmpty()) {
                    break;
                }
                // If on a new page and the page will be too long, add the current page to the pages list and start a new page
                else if (pageContent.size() <= 1 && pageContent.size() + characters.size() + 1 > MAX_PAGE_LINES) {
                    pageContent = new ArrayList<>();
                    pageContent.add(header);
                    for (Character c : characters.subList(0, MAX_PAGE_LINES - 1)) {
                        pageContent.add(generateNameLine(c));
                    }
                    characters = characters.subList(MAX_PAGE_LINES - 1, characters.size());
                    writePage(pageContent, pageContent.size());
                    pageContent = new ArrayList<>();
                }
                // If the characters don't fit on the current page, add the current page to the pages list and start a new page
                else if (pageContent.size() + characters.size() + (pageContent.size() <= 1 ? 0 : 2) > MAX_PAGE_LINES) {
                    writePage(pageContent, pageContent.size());
                    pageContent = new ArrayList<>();
                }
                // Otherwise, add the characters to the current page
                else {
                    if (!pageContent.isEmpty()) {
                        pageContent.add(Text.of(""));
                    }
                    pageContent.add(header);
                    for (Character c : characters) {
                        pageContent.add(generateNameLine(c));
                    }
                    break;
                }
            }
        }
        if (!pageContent.isEmpty()) {
            writePage(pageContent, pageContent.size());
        }
    }

    private MutableText generateNameLine(Character c) {
        MutableText nameLine = (MutableText) c.toTextWithHoverAbility(false);
        if (bookmarks.containsKey(c.id())) {
            ClickEvent click = new ClickEvent.ChangePage(bookmarks.get(c.id()));
            nameLine = nameLine.styled(style -> style.withClickEvent(click));
        }
        nameLine.append(generateJinxStars(c));
        return nameLine;
    }

    private Text generateJinxStars(Character c) {
        MutableText nameLine = Text.empty();
        List<Script.Jinx> jinxes = this.script.getJinxesForCharacter(c);
        for (Script.Jinx jinx : jinxes) {
            MutableText jinxStar = jinx.jinxStar();
            String key = "jinx_" + c.id() + "_" + jinx.id();
            if (bookmarks.containsKey(key)) {
                ClickEvent click = new ClickEvent.ChangePage(bookmarks.get(key));
                jinxStar = jinxStar.styled(style -> style.withClickEvent(click));
            } else if (bookmarks.containsKey(jinx.id())) {
                ClickEvent click = new ClickEvent.ChangePage(bookmarks.get(jinx.id()));
                jinxStar = jinxStar.styled(style -> style.withClickEvent(click));
            }
            nameLine.append(jinxStar);
        }
        return nameLine;
    }

    private void addCharacterDetailPages() {
        for (Team team : Team.values()) {
            List<Character> characters = this.script.getCharactersByTeam(team);
            if (characters.isEmpty()) {
                continue;
            }
            MutableText header = team.toText();
            header.append(" - ");
            header.append(team.getDefaultAlignment().toText());
            header.styled(style -> style.withColor(team.getColour(false)));

            Map<String, Text> items = new LinkedHashMap<>();
            for (Character character : characters) {
                MutableText item = Text.empty();
                MutableText nameLine = (MutableText) character.toFormattedText(true);
                nameLine.styled(style -> style.withBold(true).withUnderline(true));
                nameLine.append(generateJinxStars(character));
                Text abilityLine = Text.literal(character.ability() != null ? character.ability() : "No ability.");

                item.append(nameLine);
                item.append("\n");
                item.append(header);
                item.append("\n");
                item.append(abilityLine);
                items.put(character.id(), item);
            }
            writeSection(null, items);
        }
    }

    private void addBootleggerPages() {
        if (script.meta().bootlegger() == null || script.meta().bootlegger().isEmpty()) {
            return;
        }
        Text header = Text.literal("Bootlegger")
                .styled(style -> style.withColor(Team.LORIC.getColour(true)).withBold(true).withUnderline(true));
        List<Text> pageContent = new ArrayList<>();
        for (String rule : script.meta().bootlegger()) {
            pageContent.add(Text.of(rule));
        }
        writeSection(header, pageContent);
    }

    private void addJinxesPages() {
        Map<Character, List<Script.Jinx>> jinxes = script.getJinxes();
        botc.LOGGER.info(jinxes.toString());
        if (jinxes.isEmpty()) {
            return;
        }
        Text header = Text.literal("Jinxes").styled(style -> style.withColor(Team.FABLED.getColour(true))
                .withBold(true).withUnderline(true));

        Map<String, Text> pageContent = new LinkedHashMap<>();
        for (Character primary : jinxes.keySet()) {
            List<Script.Jinx> charJinxes = jinxes.get(primary);
            for (Script.Jinx jinx : charJinxes) {
                Character secondary = new Character(jinx.id());
                MutableText primaryText = (MutableText) primary.toTextWithHoverAbility(true);
                if (bookmarks.containsKey(primary.id())) {
                    ClickEvent click = new ClickEvent.ChangePage(bookmarks.get(primary.id()));
                    primaryText = primaryText.styled(style -> style.withClickEvent(click));
                }
                MutableText secondaryText = (MutableText) secondary.toTextWithHoverAbility(true);
                if (bookmarks.containsKey(secondary.id())) {
                    ClickEvent click = new ClickEvent.ChangePage(bookmarks.get(secondary.id()));
                    secondaryText = secondaryText.styled(style -> style.withClickEvent(click));
                }
                MutableText item = Text.empty()
                    .append(primaryText)
                    .append(" + ")
                    .append(secondaryText)
                    .append("\n");
                item.styled(style -> style.withBold(true).withUnderline(false));
                item.append(Text.literal(jinx.reason()).styled(style -> style.withBold(false)));
                pageContent.put("jinx_" + primary.id() + "_" + secondary.id(), item);
            }
        }
        writeSection(header, pageContent);
    }

    private void addNightOrderPages(String night, List<? extends Script.NightAction> nightOrder) {
        if (nightOrder.isEmpty()) {
            return;
        }
        Text header = Text.literal(
            switch (night) {
                case "first" -> "First Night Order";
                case "other" -> "Other Night Order";
                default -> "Night Order";
            }
        ).styled(style -> style.withBold(true).withUnderline(true));

        List<Text> pageContent = new ArrayList<>();
        for (int i = 0; i < nightOrder.size(); i++) {
            Script.NightAction nightAction = nightOrder.get(i);
            HoverEvent hover = new HoverEvent.ShowText(Text.of(nightAction.reminder));
            MutableText item = Text.empty();
            if (i < 9) {
                item.append(" ");
            }
            item.append(Text.literal((i + 1) + ": "));
            MutableText text = nightAction.toFormattedText().styled(style -> style.withHoverEvent(hover));
            if (bookmarks.containsKey(nightAction.id)) {
                ClickEvent click = new ClickEvent.ChangePage(bookmarks.get(nightAction.id));
                text = text.styled(style -> style.withClickEvent(click));
            }
            item.append(text);
            pageContent.add(item);
        }
        writeSection(header, pageContent);
    }

    private void addPlayerCountPage() {
        Text header = Text.literal("Player Counts").styled(style -> style.withBold(true).withUnderline(true));

        HashMap<Integer, int[]> counts = new HashMap<>();
        counts.put(5, new int[]{3, 0, 1, 1});
        counts.put(6, new int[]{3, 1, 1, 1});
        counts.put(7, new int[]{5, 0, 1, 1});
        counts.put(8, new int[]{5, 1, 1, 1});
        counts.put(9, new int[]{5, 2, 1, 1});
        counts.put(10, new int[]{7, 0, 2, 1});
        counts.put(11, new int[]{7, 1, 2, 1});
        counts.put(12, new int[]{7, 2, 2, 1});
        counts.put(13, new int[]{9, 0, 3, 1});
        counts.put(14, new int[]{9, 1, 3, 1});
        counts.put(15, new int[]{9, 2, 3, 1});

        List<Text> pageContent = new ArrayList<>();

        for (int n = 5; n <= 15; n++) {
            if (!counts.containsKey(n)) {
                botc.LOGGER.warn("No player count data for {} players", n);
                continue;
            }
            int[] roles = counts.get(n);
            MutableText item = Text.empty()
                .append(n < 10 ? " " : "")
                .append(Text.literal(n + ": "))
                .append(Text.literal(String.valueOf(roles[0])).styled(style -> style.withColor(Team.TOWNSFOLK.getColour(false))))
                .append(Text.literal(", "))
                .append(Text.literal(String.valueOf(roles[1])).styled(style -> style.withColor(Team.OUTSIDER.getColour(false))))
                .append(Text.literal(", "))
                .append(Text.literal(String.valueOf(roles[2])).styled(style -> style.withColor(Team.MINION.getColour(false))))
                .append(Text.literal(", "))
                .append(Text.literal(String.valueOf(roles[3])).styled(style -> style.withColor(Team.DEMON.getColour(false))));

            pageContent.add(item);
        }
        writeSection(header, pageContent);
    }


}
package org.riedelcastro.thebibbrag;

import bibtex.dom.*;
import bibtex.parser.BibtexParser;
import bibtex.parser.ParseException;

import java.io.*;
import java.util.*;

/**
 * @author sriedel
 */
public class Main {

    private static HashMap<String, String> macros = new HashMap<String, String>();

    public static String normalize(String input) {
        return input.replace("{", "").replace("}", "")
                .replace("\\\"u", "�")
                .replace("\\\"a", "�");
    }

    public static String normalize(BibtexAbstractValue value) {
        if (value == null) return "N/A";
        if (value instanceof BibtexString) {
            BibtexString bibtexString = (BibtexString) value;
            return normalize(bibtexString.getContent());
        }
        if (value instanceof BibtexConcatenatedValue) {
            BibtexConcatenatedValue concatenatedValue = (BibtexConcatenatedValue) value;
            return normalize(concatenatedValue.getLeft()) + " " + normalize(concatenatedValue.getRight());
        }
        if (value instanceof BibtexPersonList) {
            BibtexPersonList personList = (BibtexPersonList) value;
            int index = 0;
            StringBuffer list = new StringBuffer();
            for (Object o : personList.getList()) {
                if (o instanceof BibtexPerson) {
                    if (index > 0) list.append(", ");
                    BibtexPerson person = (BibtexPerson) o;
                    ++index;
                    list.append(person.getFirst()).append(" ").append(person.getLast());
                }
            }
            return list.toString();
        }
        if (value instanceof BibtexMacroReference) {
            BibtexMacroReference macroReference = (BibtexMacroReference) value;
            return macros.get(macroReference.getKey());
        }

        return normalize(value.toString());
    }

    private static String readFileAsString(String filePath) throws java.io.IOException {
        byte[] buffer = new byte[(int) new File(filePath).length()];
        BufferedInputStream f = new BufferedInputStream(new FileInputStream(filePath));
        f.read(buffer);
        return new String(buffer);
    }

    private static final String preamble =
            "<html><head></head><body>";
    private static final String postamble =
            "</body></html>";

    private static String bibtex(BibtexEntry entry) {
        StringBuffer result = new StringBuffer();
        result.append(String.format("@%s{%s,\n", entry.getEntryType(),entry.getEntryKey()));
        for (Object key : entry.getFields().keySet()) {
            String keyString = key.toString();
            if (!keyString.startsWith("date") && !keyString.startsWith("url"))
                result.append(String.format("    %s={%s}, \n", keyString, normalize(entry.getFieldValue(keyString))));
        }

        result.append("}");
        return result.toString();
    }

    private static String createPerPubHTML(File dir, BibtexEntry entry, String preamble, String postamble)
            throws FileNotFoundException, UnsupportedEncodingException {
        String entryKey = entry.getEntryKey();
        String author = normalize(entry.getFieldValue("author"));
        String year = normalize(entry.getFieldValue("year"));
        String stringTitle = normalize(entry.getFieldValue("title"));
        String filename = String.format("%s.html", entryKey);
        PrintStream html = new PrintStream(new File(dir, filename), "UTF-8");

        html.println(preamble);
//        html.println(String.format("<h2>Details</h2>"));
        printBibItem(html, entry);

//        html.println(String.format("<h2>Bibtex</h2>"));
        html.println("<blockquote><pre>");
        html.println(bibtex(entry));
        html.println("</pre></blockquote>");
        html.println(postamble);

        html.close();

        return filename;
    }

    private static void printPubItem(PrintStream htmlOverview, BibtexEntry entry) {

    }

    private static HashMap<String,String> monthMapping = new HashMap<String,String>();

    private static void setupMonthMapping() {
        monthMapping.put("1","01");
        monthMapping.put("2","02");
        monthMapping.put("3","03");
        monthMapping.put("4","04");
        monthMapping.put("5","05");
        monthMapping.put("6","06");
        monthMapping.put("7","07");
        monthMapping.put("8","08");
        monthMapping.put("9","09");
    }

    private static String sortKey(BibtexEntry entry) {
        BibtexAbstractValue month = entry.getFieldValue("month");
        if (month != null) {
            String m = normalize(month).trim().toLowerCase();
            String mapped = monthMapping.get(m);
            return mapped == null ? m : mapped;
        }
        return normalize(entry.getFieldValue("title"));
    }

    private static List<BibtexEntry> sortEntries(List<BibtexEntry> entries) {
        ArrayList<BibtexEntry> result = new ArrayList<BibtexEntry>(entries);
        Collections.sort(result, new Comparator<BibtexEntry>() {
            public int compare(BibtexEntry bibtexEntry1, BibtexEntry bibtexEntry2) {
                String key1 = sortKey(bibtexEntry1);
                String key2 = sortKey(bibtexEntry2);
                return key1.compareTo(key2);
            }
        });

        return result;
    }

    public static void main(String[] args) throws IOException, ParseException {
        setupMonthMapping();
        BibtexFile bib = new BibtexFile();
        BibtexParser parser = new BibtexParser(false);
        parser.parse(bib, new FileReader(args[0]));
        for (ParseException e : parser.getExceptions()) {
            System.out.println(e.toString());
        }
        String targetDirString = args[1];
        String groupBy = args[2];
        String preamble = args.length > 3 ? readFileAsString(args[3]) : Main.preamble;
        String postamble = args.length > 3 ? readFileAsString(args[4]) : Main.postamble;

        File targetDir = new File(targetDirString);
        File details = new File(targetDir, "details");
        details.mkdirs();
        PrintStream overviewHTML = new PrintStream(new File(targetDir, "all.html"), "UTF-8");

        for (Object o : bib.getEntries()) {
            if (o instanceof BibtexMacroDefinition) {
                BibtexMacroDefinition macroDefinition = (BibtexMacroDefinition) o;
                System.out.println(macroDefinition.getKey() + "->" + normalize(macroDefinition.getValue()));
                macros.put(macroDefinition.getKey(), normalize(macroDefinition.getValue()));
            }
        }


        HashMap<String, List<BibtexEntry>> grouping = new HashMap<String, List<BibtexEntry>>();

        for (Object o : bib.getEntries()) {
            if (o instanceof BibtexEntry) {
                BibtexEntry entry = (BibtexEntry) o;
                String author = normalize(entry.getFieldValue("author"));
                String year = normalize(entry.getFieldValue("year"));
                if (!author.contains("Riedel") || year.equals("N/A")) continue;
                BibtexAbstractValue value = entry.getFieldValue(groupBy);
                if (value != null) {
                    for (String groupKey : normalize(value).split(",")) {
                        List<BibtexEntry> group = grouping.get(groupKey);
                        if (group == null) {
                            group = new ArrayList<BibtexEntry>();
                            grouping.put(groupKey, group);
                        }
                        group.add(entry);
                    }
                }
            }
        }

        ArrayList<String> sorted = new ArrayList<String>(grouping.keySet());
        Collections.sort(sorted);
        Collections.reverse(sorted);

        overviewHTML.println(preamble);

        overviewHTML.println("<ul class=\"grouplist\">\n");
        for (String groupKey : sorted) {
            overviewHTML.println(String.format("<li class=\"groupref\"><a href=\"#%s\">%s</a></li>", groupKey, groupKey));
        }
        overviewHTML.println("</ul>");

        overviewHTML.println("<ul class=\"publist\">\n");

        for (String groupKey : sorted) {
            overviewHTML.println(String.format("<li class=\"group\"><a name=\"%s\">" +
                    "<span class=\"grouptitle\">%s</span></a>\n",
                    groupKey, groupKey));
            overviewHTML.println("<ul class=\"itemlist\">\n");
            for (BibtexEntry entry : sortEntries(grouping.get(groupKey))) {

                BibtexAbstractValue title = entry.getFieldValue("title");
                if (title != null) {
                    String author = normalize(entry.getFieldValue("author"));
                    String year = normalize(entry.getFieldValue("year"));
                    String stringTitle = normalize(entry.getFieldValue("title"));
                    if (!author.contains("Riedel") || year.equals("N/A") || stringTitle.contains("http")) continue;
                    String filename = createPerPubHTML(details, entry, preamble, postamble);
                    overviewHTML.println("<li class=\"pubitem\">");
                    printBibItem(overviewHTML, entry);
                    overviewHTML.println(String.format("[<a href=\"details/%s\">details</a>]", filename));
                    overviewHTML.println("</li>");

                }

            }
            overviewHTML.println("</ul>\n");
            overviewHTML.println("</li>");

        }

        overviewHTML.println("</ul>\n");
        overviewHTML.println(postamble);

    }

    private static void printBibItem(PrintStream overviewHTML, BibtexEntry entry) {
        String author = normalize(entry.getFieldValue("author"));
        String year = normalize(entry.getFieldValue("year"));
        String stringTitle = normalize(entry.getFieldValue("title"));
        System.out.println(stringTitle);
        System.out.println(normalize(author));
        overviewHTML.println(String.format("<span class=\"title\">%s</span>, ",
                stringTitle));
        overviewHTML.println(String.format("<span class=\"author\">%s</span>, ",
                author));
        if (entry.getEntryType().startsWith("inproceedings")) {
            overviewHTML.println(String.format("<span class=\"booktitle\">%s</span>",
                    normalize(entry.getFieldValue("booktitle"))));
        } else if (entry.getEntryType().startsWith("article")) {
            overviewHTML.println(String.format("<span class=\"journal\">%s</span>",
                    normalize(entry.getFieldValue("journal"))));
        } else if (entry.getEntryType().startsWith("misc")) {
            overviewHTML.println(String.format("<span class=\"howpublished\">%s</span>",
                    normalize(entry.getFieldValue("howpublished"))));
        } else if (entry.getEntryType().startsWith("phdthesis")) {
            overviewHTML.println(String.format("<span class=\"school\">%s</span>",
                    normalize(entry.getFieldValue("school"))));
        }


        BibtexAbstractValue note = entry.getFieldValue("note");

        if (note != null){
            overviewHTML.println(String.format("<span class=\"note\">%s</span>", normalize(note)));
        }

        overviewHTML.println(String.format("<span class=\"year\">%s</span>",
                year));

        BibtexAbstractValue url = entry.getFieldValue("url");
        if (url != null) {
            overviewHTML.println(String.format("[<a href=\"%s\">pdf</a>]", normalize(url)));
        }
    }

}

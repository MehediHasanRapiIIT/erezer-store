package kn.org.deliverybackend.service.impl;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.Set;

/**
 * The plain-text version of an HTML email: the same words, one block per line,
 * and each link written out after its text. Mail services trust an email with
 * both versions more than one with HTML alone, and some people read only text.
 */
final class EmailText {

    private static final Set<String> BLOCKS = Set.of(
            "p", "div", "h1", "h2", "h3", "h4", "tr", "table", "li", "ul", "ol", "section", "header", "footer");

    private EmailText() {
    }

    static String plain(String html) {
        Document doc = Jsoup.parse(html == null ? "" : html);
        doc.select("style, script, head, [data-plain=skip]").remove();
        StringBuilder out = new StringBuilder();
        walk(doc.body(), out);
        return out.toString()
                .replace(' ', ' ')
                .replaceAll("[ \\t]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim() + "\n";
    }

    private static void walk(Node node, StringBuilder out) {
        if (node instanceof TextNode t) {
            out.append(t.text());
            return;
        }
        if (!(node instanceof Element el)) {
            return;
        }
        String tag = el.tagName();
        if ("br".equals(tag)) {
            out.append('\n');
            return;
        }
        if ("img".equals(tag)) {
            return;
        }
        boolean block = BLOCKS.contains(tag);
        if (block) out.append('\n');
        if ("td".equals(tag) || "th".equals(tag)) out.append(' ');
        for (Node child : el.childNodes()) {
            walk(child, out);
        }
        if ("a".equals(tag)) {
            String href = el.attr("href");
            String text = el.text().trim();
            if (!href.isBlank() && !href.startsWith("#") && !href.equals(text) && !href.equals("mailto:" + text)) {
                out.append(text.isEmpty() ? href : ": " + href);
            }
        }
        if (block) out.append('\n');
    }
}

package wonder.shaderdisplay.entry;

import fr.wonder.commons.loggers.Logger;
import wonder.shaderdisplay.Main;
import wonder.shaderdisplay.Resources;

import java.util.List;
import java.util.regex.PatternSyntaxException;

public class EntrySnippets {

    public static void run(Main.SnippetsOptions options) {
        Main.logger.setLogLevel(options.verbose ? Logger.LEVEL_DEBUG : Logger.LEVEL_INFO);
        Resources.scanForAndLoadSnippets();
        List<Resources.Snippet> snippets;
        try {
            snippets = Resources.filterSnippets(options.filter);
        } catch (PatternSyntaxException e) {
            Main.logger.err(e, "Invalid filter");
            return;
        }
        if(snippets.isEmpty()) {
            System.err.println("No matching snippets");
            return;
        }
        for(Resources.Snippet s : snippets) {
            if(options.printCode)
                System.out.println(s.code);
            else
                System.out.println(s.name);
        }
        if(!options.printCode) {
            System.out.println("Found " + snippets.size() + " snippets, run with -c to print their codes");
        }
    }

}

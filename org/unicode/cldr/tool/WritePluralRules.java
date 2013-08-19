package org.unicode.cldr.tool;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.unicode.cldr.unittest.TestAll;
import org.unicode.cldr.util.CLDRConfig;
import org.unicode.cldr.util.SupplementalDataInfo;
import org.unicode.cldr.util.SupplementalDataInfo.PluralType;

import com.ibm.icu.dev.util.CollectionUtilities;
import com.ibm.icu.dev.util.Relation;
import com.ibm.icu.text.PluralRules;
import com.ibm.icu.text.PluralRules.FixedDecimal;
import com.ibm.icu.util.ULocale;

public class WritePluralRules {
    static SupplementalDataInfo sInfo = CLDRConfig.getInstance().getSupplementalDataInfo();

    public static void main(String[] args) {
        Relation<PluralRules,String> rulesToLocales = Relation.of(new TreeMap<PluralRules,Set<String>>(new PluralRulesComparator()), TreeSet.class);
        for (String locale : sInfo.getPluralLocales(PluralType.cardinal)) {
            if (locale.equals("root")) {
                continue;
            }
            PluralRules rules = forLocale(locale);
//            PluralRules existingRules = stringToRules.get(rules.toString());
//            if (existingRules == null) {
//                stringToRules.put(rules.toString(), existingRules = rules);
//            }
            rulesToLocales.put(rules, locale);
        }
        writePluralHeader(PluralType.cardinal);
        TreeSet<Entry<PluralRules, Set<String>>> sorted = new TreeSet<Entry<PluralRules, Set<String>>>(new HackComparator());
        sorted.addAll(rulesToLocales.keyValuesSet());
        for (Entry<PluralRules, Set<String>> entry : sorted) {
            PluralRules rules = entry.getKey();
            Set<String> values = entry.getValue();
            //String comment = hackComments.get(locales);
            writePluralRuleHeader(values);
            for (String keyword : rules.getKeywords()) {
                String rule = rules.getRules(keyword);
                if (rule == null) {
                    continue;
                }
                writePluralRule(keyword, rule);
            }
            writePluralRuleFooter();
            /*
        <pluralRules locales="ar">
            <pluralRule count="zero">n is 0</pluralRule>
            <pluralRule count="one">n is 1</pluralRule>
            <pluralRule count="two">n is 2</pluralRule>
            <pluralRule count="few">n mod 100 in 3..10</pluralRule>
            <pluralRule count="many">n mod 100 in 11..99</pluralRule>
        </pluralRules>

             */
        }
        writePluralFooter();
    }

    public static void writePluralRuleFooter() {
        System.out.println("        </pluralRules>");
    }

    public static void writePluralRule(String keyword, String rule) {
        System.out.println("            <pluralRule count=\"" + keyword + "\">" + rule + "</pluralRule>");
    }

    public static void writePluralRuleHeader(Set<String> values) {
        String locales = CollectionUtilities.join(values, " ");
        System.out.println("        <pluralRules locales=\"" + locales + "\">"
                //+ (comment != null ? comment : "")
                );
    }

    public static void writePluralFooter() {
        System.out.println("    </plurals>\n" +
        		"</supplementalData>");
    }

    public static void writePluralHeader(PluralType type) {
        System.out.println(
                "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n"
                        +"<!DOCTYPE supplementalData SYSTEM \"../../common/dtd/ldmlSupplemental.dtd\">\n"
                        +"<!--\n"
                        +"Copyright © 1991-2013 Unicode, Inc.\n"
                        +"CLDR data files are interpreted according to the LDML specification (http://unicode.org/reports/tr35/)\n"
                        +"For terms of use, see http://www.unicode.org/copyright.html\n"
                        +"-->\n"
                        +"<supplementalData>\n"
                        +"    <version number=\"$Revision" +
                        "$\"/>\n"
                        +"    <generation date=\"$Date" +
                        "$\"/>\n"
                        +"    <plurals type=\"" + type + "\">\n"
                        +"        <!-- For a canonicalized list, use WritePluralRules -->\n"
                        +"        <!-- if locale is known to have no plurals, there are no rules -->"
                );
    }
    
//    static Map<String,String> hackComments = new HashMap<String,String>();
//    static {
//        hackComments.put("ga", " <!-- http://unicode.org/cldr/trac/ticket/3915 -->");
//        hackComments.put("mt", " <!-- from Tamplin's data -->");
//        hackComments.put("mk", " <!-- from Tamplin's data -->");
//        hackComments.put("cy", " <!-- from http://www.saltcymru.org/wordpress/?p=99&lang=en -->");
//        hackComments.put("br", " <!-- from http://unicode.org/cldr/trac/ticket/2886 -->");
//    }
    
    static class HackComparator implements Comparator<Entry<PluralRules, Set<String>>> {
        // we get the order of the first items in each of the old rules, and use that order where we can.
        PluralRulesComparator prc = new PluralRulesComparator();
        static Map<String,Integer> hackMap = new HashMap<String,Integer>();
        static {
            int i = 0;
            for (String s : "az ar he asa af lg vo ak ff lv iu ga ro mo lt be cs sk pl sl mt mk cy lag shi br ksh tzm gv gd".split(" ")) {
                hackMap.put(s, i++);
            }
        }
        @Override
        public int compare(Entry<PluralRules, Set<String>> o1, Entry<PluralRules, Set<String>> o2) {
            Integer firstLocale1 = hackMap.get(o1.getValue().iterator().next());
            Integer firstLocale2 = hackMap.get(o2.getValue().iterator().next());
            if (firstLocale1 != null) {
                if (firstLocale2 != null) {
                    return firstLocale1 - firstLocale2;
                }
                return -1;
            } else if (firstLocale2 != null) {
                return 1;
            } else { // only if BOTH are null, use better comparison
                return prc.compare(o1.getKey(), o2.getKey());
            }
        }
    }
    
    static class PluralRulesComparator implements Comparator<PluralRules> {
        CollectionUtilities.CollectionComparator<String> comp = new CollectionUtilities.CollectionComparator<String>();

        @Override
        public int compare(PluralRules arg0, PluralRules arg1) {
            Set<String> key0 = arg0.getKeywords();
            Set<String> key1 = arg1.getKeywords();
            int diff = comp.compare(key0, key1);
            if (diff != 0) {
                return diff;
            }
            return arg0.toString().compareTo(arg1.toString());
        }
    }
    
    static PluralRules forLocale(String locale) {
        PluralRules override = null; // PluralRulesFactory.getPluralOverrides().get(new ULocale(locale));
        return override != null 
                ? override
                        : sInfo.getPlurals(locale).getPluralRules();
    }
}
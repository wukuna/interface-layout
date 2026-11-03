package com.interfacelayout.layout.equipment;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemComposition;
import net.runelite.api.ParamID;
import net.runelite.api.StructComposition;
import net.runelite.api.VarPlayer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.util.Text;

final class EquipmentDetails {

    private static final int SET_DESCRIPTIONS = 3047;
    private EquipmentDetails() {}

    static String capture(Client client, ItemManager items) {
        ItemContainer worn = client.getItemContainer(InventoryID.WORN);
        if (worn == null) return "Equipment details unavailable";
        Set<String> names = new LinkedHashSet<>();
        Item[] equipped = worn.getItems();
        for (Item item : equipped) if (item != null && item.getId() >= 0 && item.getQuantity() > 0) {
            ItemComposition definition = items.getItemComposition(item.getId());
            if (definition != null && definition.getName() != null)
                names.add(definition.getName().toLowerCase(Locale.ROOT));
        }
        Item weapon = equipped.length > 3 ? equipped[3] : null;
        int base = 4;
        String weaponName = "Unarmed";
        if (weapon != null && weapon.getId() >= 0 && weapon.getQuantity() > 0) {
            ItemStats stats = items.getItemStats(weapon.getId());
            base = stats == null || stats.getEquipment() == null ? 0 : stats.getEquipment().getAspeed();
            ItemComposition definition = items.getItemComposition(weapon.getId());
            weaponName = definition == null ? "Weapon" : definition.getName();
        }
        String style = style(client);
        int selectedStyle = client.getVarpValue(VarPlayer.ATTACK_STYLE);
        int adjusted = adjustedSpeed(base, weaponName, style, selectedStyle,
            client.getVarbitValue(VarbitID.SPELLBOOK));
        List<String> lines = new ArrayList<>();
        lines.add("<col=ffffff>Weapon speed</col>");
        lines.add("Base: " + interval(base));
        lines.add("Adjusted (style/gear): " + interval(adjusted));

        List<String> effects = new ArrayList<>();
        int set = client.getVarbitValue(VarbitID.WORN_ITEMS_SET_TEXT);
        EnumComposition descriptions = client.getEnum(SET_DESCRIPTIONS);
        if (set > 0 && descriptions != null) {
            String text = descriptions.getStringValue(set);
            if (text != null && !text.isBlank() && !"null".equals(text)) effects.add(plain(text));
        }

        active(client,effects,VarbitID.WORN_ITEMS_CRYSTAL_ARMOUR,"Crystal armour: boosts accuracy and damage with crystal bows; the bonus depends on the pieces worn.");
        active(client,effects,VarbitID.WORN_ITEMS_INQUISITORS_ARMOUR,"Inquisitor's armour: boosts crush accuracy and damage; the bonus depends on the pieces worn.");
        active(client,effects,VarbitID.WORN_ITEMS_VIRTUSSET,"Virtus robes: additional damage when casting Ancient Magicks.");
        active(client,effects,VarbitID.WORN_ITEMS_SWAMPBARK_SET,"Swampbark: longer Bind, Snare and Entangle duration per qualifying piece.");
        active(client,effects,VarbitID.WORN_ITEMS_BLOODBARK_SET,"Bloodbark: increased healing from Blood spells per piece.");
        active(client,effects,VarbitID.WORN_ITEMS_PADOS_ROBES_SET,"Zealot's robes: chance to save prayer-training supplies per piece.");
        active(client,effects,VarbitID.WORN_ITEMS_GRACEFUL_SET,"Graceful: faster run-energy restoration; the bonus depends on pieces worn.");
        active(client,effects,VarbitID.WORN_ITEMS_RC_OUTFIT,"Raiments of the Eye: extra runes from Runecraft.");
        active(client,effects,VarbitID.WORN_ITEMS_SMITHING_UNIFORM,"Smiths' uniform: chance to smith faster.");
        active(client,effects,VarbitID.WORN_ITEMS_SPIRIT_ANGLER,"Spirit angler: Fishing experience bonus; the complete outfit works as a rope at Tempoross.");
        active(client,effects,VarbitID.WORN_ITEMS_TRAWLER_BONUS,"Angler: Fishing experience bonus.");
        active(client,effects,VarbitID.WORN_ITEMS_LUMBERJACK_FULL_OUTFIT,"Lumberjack: Woodcutting experience bonus.");
        active(client,effects,VarbitID.WORN_ITEMS_PROSPECTOR_SET,"Prospector: Mining experience bonus.");
        active(client,effects,VarbitID.WORN_ITEMS_FARMING_SET,"Farmer: Farming experience bonus.");
        active(client,effects,VarbitID.WORN_ITEMS_PYROMANCER_SET,"Pyromancer: Firemaking experience bonus.");
        active(client,effects,VarbitID.WORN_ITEMS_ROGUE_SET,"Rogue: chance of double pickpocket loot.");
        active(client,effects,VarbitID.WORN_ITEMS_CARPENTER_SET,"Carpenter: Construction experience bonus.");
        active(client,effects,VarbitID.WORN_ITEMS_DIZANAS_QUIVER_CHARGED,"Dizana's Sunfire: extra Ranged attack and strength when using arrows or bolts.");
        active(client,effects,VarbitID.WORN_ITEMS_ZARYTEXBOW,"Zaryte crossbow: stronger enchanted-bolt effects.");
        active(client,effects,VarbitID.WORN_ITEMS_TUMEKENS_SHADOW,"Tumeken's shadow: multiplies Magic attack and damage bonuses from worn gear when using its built-in spell.");
        if (has(names,"blood moon helm") && has(names,"blood moon chestplate")
            && has(names,"blood moon tassets") && has(names,"dual macuahuitl"))
            effects.add("Blood moon's speed effect is conditional; a proc can shorten the next attack interval.");
        lines.add("<col=ffffff>Set & equipment effects</col>");
        if (effects.isEmpty()) lines.add(set > 0 ? "Set description unavailable" : "No set effect reported by the game");
        else lines.addAll(effects);
        List<String> targets = targetEffects(names);
        active(client,targets,VarbitID.WORN_ITEMS_TWISTED_BOW,"Twisted bow: accuracy and damage scale with the target's magical strength.");
        active(client,targets,VarbitID.WORN_ITEMS_SCYTHE_OF_VITUR,"Scythe: additional hits against large targets or multiple nearby targets.");
        active(client,targets,VarbitID.WORN_ITEMS_KERISPARTISANSUN,"Sun partisan in Tombs of Amascut: extra accuracy against targets below 25% health; kills can trade Prayer for healing.");
        active(client,targets,VarbitID.WORN_ITEMS_SHAYZIEN_SET,"Shayzien armour: protection against lizardman shaman acid attacks.");
        lines.add("<col=ffffff>Target-specific conditions</col>");
        lines.addAll(targets.isEmpty() ? java.util.Collections.singletonList("No supported target bonus identified") : targets);
        return wrap(lines);
    }

    static int adjustedSpeed(int base, String weapon, String style, int selectedStyle, int spellbook) {
        if (base <= 0) return 0;
        String name = weapon == null ? "" : weapon.toLowerCase(Locale.ROOT);
        if (name.startsWith("obligator") && selectedStyle >= 2) return 3;
        if ("Rapid".equalsIgnoreCase(style)) return Math.max(1,base-1);
        if ("Casting".equalsIgnoreCase(style) || "Defensive casting".equalsIgnoreCase(style)) {
            if (name.equals("harmonised nightmare staff") && spellbook == 0) return 4;
            if (name.equals("twinflame staff")) return 6;
            return 5;
        }

        return style == null ? 0 : base;
    }

    private static String style(Client client) {
        int category = client.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY);
        int selection = client.getVarpValue(VarPlayer.ATTACK_STYLE);
        if (selection < 0) return null;

        if (category == 22) return selection == 4 ? "Casting" : "Melee";
        if (category == 30) return "Melee";
        EnumComposition categories = client.getEnum(EnumID.WEAPON_STYLES);
        if (categories == null) return null;
        int enumId = categories.getIntValue(category);
        if (enumId < 0) return null;
        EnumComposition styles = client.getEnum(enumId);
        if (styles == null || styles.getIntVals() == null || selection >= styles.getIntVals().length) return null;
        StructComposition value = client.getStructComposition(styles.getIntVals()[selection]);
        if (value == null) return null;
        String name = value.getStringValue(ParamID.ATTACK_STYLE_NAME);

        return selection == 4 ? "Casting" : name;
    }

    static List<String> targetEffects(Set<String> names) {
        List<String> result = new ArrayList<>();
        if (has(names,"slayer helmet") || has(names,"black mask"))
            result.add("Slayer headgear: bonus against assigned Slayer targets; imbued versions also support Ranged and Magic.");
        if (has(names,"salve amulet"))
            result.add("Salve amulet: bonus against undead; (e) improves it and (i) extends it to Ranged and Magic. Does not stack with Slayer headgear.");
        if (has(names,"dragon hunter")) result.add("Dragon hunter weapon: increased accuracy and damage against draconic targets.");
        if (has(names,"arclight") || has(names,"emberlight") || has(names,"darklight") || has(names,"scorching bow"))
            result.add("Demonbane weapon: increased effectiveness against demons; resistance varies by target.");
        if (has(names,"silverlight")) result.add("Silverlight: increased effectiveness against demons.");
        if (has(names,"keris")) result.add("Keris: extra damage and a chance of a stronger hit against kalphites and scabarites.");
        if (has(names,"leaf-bladed")) result.add("Leaf-bladed weapon: can damage turoths and kurasks; the battleaxe also gains bonus damage.");
        if (has(names,"crystal halberd")) result.add("Crystal halberd: its special attack gains a second hit against large targets.");
        if (has(names,"craw's bow") || has(names,"webweaver bow") || has(names,"viggora's chainmace")
            || has(names,"ursine chainmace") || has(names,"thammaron's sceptre") || has(names,"accursed sceptre"))
            result.add("Wilderness weapon: charged variants gain a bonus against NPCs in the Wilderness.");
        if (has(names,"bone mace") || has(names,"bone shortbow") || has(names,"bone staff"))
            result.add("Rat bone weapon: bonus damage against rats; attacks against Scurrius' giant rats can be one tick apart.");
        return result;
    }

    private static boolean has(Set<String> names, String part) {
        for (String name : names) if (name.contains(part)) return true;
        return false;
    }
    private static void active(Client client,List<String> lines,int bit,String text) {
        if (client.getVarbitValue(bit) > 0) lines.add(text);
    }
    private static String interval(int ticks) {
        return ticks <= 0 ? "Unavailable" : String.format(Locale.ROOT,"%d ticks (%.1f s)",ticks,ticks*0.6);
    }
    private static String plain(String text) {
        return Text.removeTags(text.replace("<br>"," ")).replaceAll("\\s+"," ").trim();
    }
    static String wrap(List<String> paragraphs) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : paragraphs) {
            if (paragraph.startsWith("<col=ffffff>") && paragraph.endsWith("</col>")
                && !paragraph.contains("<br>") && plain(paragraph).length() < 40) {
                lines.add(paragraph); continue;
            }
            StringBuilder line = new StringBuilder();
            for (String word : plain(paragraph).split(" ")) {
                if (line.length() > 0 && line.length()+word.length()+1 > 55) {
                    lines.add(line.toString());line.setLength(0);
                }
                if (line.length() > 0) line.append(' ');
                line.append(word);
            }
            if (line.length()>0) lines.add(line.toString());
        }
        return String.join("<br>",lines);
    }
}

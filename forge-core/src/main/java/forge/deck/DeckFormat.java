/*
 * Forge: Play Magic: the Gathering.
 * Copyright (C) 2011  Forge Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package forge.deck;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import forge.StaticData;
import forge.card.*;
import forge.deck.generation.DeckGenPool;
import forge.deck.generation.DeckGeneratorBase.FilterCMC;
import forge.deck.generation.IDeckGenPool;
import forge.item.IPaperCard;
import forge.item.PaperCard;
import forge.item.PaperCardPredicates;
import forge.util.Aggregates;
import forge.util.TextUtil;
import org.apache.commons.lang3.Range;
import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Predicate;

/**
 * GameType is an enum to determine the type of current game. :)
 */
public enum DeckFormat {
    //               Main board: allowed size         SB: restriction  Max distinct non-basic cards
    Constructed    ( Range.of(60, Integer.MAX_VALUE), Range.of(0, 15), 4),
    QuestDeck      ( Range.of(40, Integer.MAX_VALUE), Range.of(0, 15), 4),
    Limited        ( Range.of(40, Integer.MAX_VALUE), null, Integer.MAX_VALUE) {
        @Override
        public String getAttractionDeckConformanceProblem(Deck deck) {
            //Limited attraction decks have a minimum size of 3 and no singleton restriction.
            if (deck.get(DeckSection.Attractions).countAll() < 3)
                return "must contain at least 3 attractions, or none at all";
            return null;
        }

        @Override
        public String getContraptionDeckConformanceProblem(Deck deck) {
            //Limited contraption decks have no restrictions.
            return null;
        }
    },
    Commander      ( Range.is(99),                         Range.of(0, 10), 1, null,
            card -> StaticData.instance().getCommanderPredicate().test(card)
    ),
    Oathbreaker      ( Range.is(58),                         Range.of(0, 10), 1, null,
            card -> StaticData.instance().getOathbreakerPredicate().test(card)
    ),
    Pauper      ( Range.is(60),                         Range.of(0, 10), 1),
    Brawl      ( Range.is(59), Range.of(0, 15), 1, null,
            card -> StaticData.instance().getBrawlPredicate().test(card)
    ),
    TinyLeaders    ( Range.is(49),                         Range.of(0, 10), 1, new Predicate<>() {
        private final Set<String> bannedCards = ImmutableSet.of(
                "Ancestral Recall", "Balance", "Black Lotus", "Black Vise", "Channel", "Chaos Orb", "Contract From Below", "Counterbalance", "Darkpact", "Demonic Attorney", "Demonic Tutor", "Earthcraft", "Edric, Spymaster of Trest", "Falling Star",
                "Fastbond", "Flash", "Goblin Recruiter", "Grindstone", "Hermit Druid", "Imperial Seal", "Jeweled Bird", "Karakas", "Library of Alexandria", "Mana Crypt", "Mana Drain", "Mana Vault", "Metalworker", "Mind Twist", "Mishra's Workshop",
                "Mox Emerald", "Mox Jet", "Mox Pearl", "Mox Ruby", "Mox Sapphire", "Najeela, the Blade Blossom", "Necropotence", "Shahrazad", "Skullclamp", "Sol Ring", "Strip Mine", "Survival of the Fittest", "Sword of Body and Mind", "Time Vault", "Time Walk", "Timetwister",
                "Timmerian Fiends", "Tolarian Academy", "Umezawa's Jitte", "Vampiric Tutor", "Wheel of Fortune", "Yawgmoth's Will");

        @Override
        public boolean test(CardRules rules) {
            // Check for split cards explicitly, as using rules.getManaCost().getCMC()
            // will return the sum of the costs, which is not what we want.
            if (rules.getMainPart().getManaCost().getCMC() > 3) {
                return false; // Only cards with CMC less than 3 are allowed
            }
            ICardFace otherPart = rules.getOtherPart();
            if (otherPart != null && otherPart.getManaCost().getCMC() > 3) {
                return false; // Only cards with CMC less than 3 are allowed
            }
            return !bannedCards.contains(rules.getName());
        }
    }) {
        private final Set<String> bannedCommanders = ImmutableSet.of("Derevi, Empyrial Tactician", "Erayo, Soratami Ascendant", "Rofellos, Llanowar Emissary");

        @Override
        public boolean isLegalCommander(CardRules rules) {
            return super.isLegalCommander(rules) && !bannedCommanders.contains(rules.getName());
        }

        @Override
        public void adjustCMCLevels(List<ImmutablePair<FilterCMC, Integer>> cmcLevels) {
            cmcLevels.clear();
            cmcLevels.add(ImmutablePair.of(new FilterCMC(0, 1), 3));
            cmcLevels.add(ImmutablePair.of(new FilterCMC(2, 2), 3));
            cmcLevels.add(ImmutablePair.of(new FilterCMC(3, 3), 3));
        }
    },
    PlanarConquest ( Range.of(40, Integer.MAX_VALUE), Range.is(0), 1),
    Adventure      ( Range.of(40, Integer.MAX_VALUE), Range.of(0, 15), 4),
    Vanguard       ( Range.of(60, Integer.MAX_VALUE), Range.is(0), 4),
    Planechase     ( Range.of(60, Integer.MAX_VALUE), Range.is(0), 4),
    Archenemy      ( Range.of(60, Integer.MAX_VALUE), Range.is(0), 4),
    Puzzle         ( Range.of(0, Integer.MAX_VALUE), Range.is(0), 4);

    private final Range<Integer> mainRange;
    private final Range<Integer> sideRange; // null => no check
    private final int maxCardCopies;
    private final Predicate<CardRules> cardPoolFilter;
    private final Predicate<PaperCard> paperCardPoolFilter;
    private final static String ADVPROCLAMATION = "Advantageous Proclamation";
    // private final static String SOVREALM = "Sovereign's Realm";

    DeckFormat(Range<Integer> mainRange0, Range<Integer> sideRange0, int maxCardCopies0, Predicate<CardRules> cardPoolFilter0, Predicate<PaperCard> paperCardPoolFilter0) {
        mainRange = mainRange0;
        sideRange = sideRange0;
        maxCardCopies = maxCardCopies0;
        cardPoolFilter = cardPoolFilter0;
        paperCardPoolFilter = paperCardPoolFilter0;
    }

    DeckFormat(Range<Integer> mainRange0, Range<Integer> sideRange0, int maxCardCopies0, Predicate<CardRules> cardPoolFilter0) {
        mainRange = mainRange0;
        sideRange = sideRange0;
        maxCardCopies = maxCardCopies0;
        paperCardPoolFilter = null;
        cardPoolFilter = cardPoolFilter0;
    }

    DeckFormat(Range<Integer> mainRange0, Range<Integer> sideRange0, int maxCardCopies0) {
        mainRange = mainRange0;
        sideRange = sideRange0;
        maxCardCopies = maxCardCopies0;
        paperCardPoolFilter = null;
        cardPoolFilter = null;
    }

    public boolean hasCommander() {
        return this == Commander || this == Oathbreaker || this == TinyLeaders || this == Brawl;
    }

    public boolean hasSignatureSpell() {
        return this == Oathbreaker;
    }

    /**
     * Smart value of.
     *
     * @param value the value
     * @param defaultValue the default value
     * @return the game type
     */
    public static DeckFormat smartValueOf(final String value, DeckFormat defaultValue) {
        if (value == null) {
            return defaultValue;
        }

        final String valToCompate = value.trim();
        for (final DeckFormat v : DeckFormat.values()) {
            if (v.name().compareToIgnoreCase(valToCompate) == 0) {
                return v;
            }
        }

        throw new IllegalArgumentException("No element named " + value + " in enum GameType");
    }

    /**
     * @return the sideRange
     */
    public Range<Integer> getSideRange() {
        return sideRange;
    }

    /**
     * @return the mainRange
     */
    public Range<Integer> getMainRange() {
        return mainRange;
    }

    /**
     * @return the maxCardCopies
     */
    public int getMaxCardCopies() {
        return maxCardCopies;
    }

    public String getDeckConformanceProblem(Deck deck) {
        if (deck == null) {
            return "is not selected";
        }

        int deckSize = deck.getMain().countAll();

        int min = getMainRange().getMinimum();
        int max = getMainRange().getMaximum();
        // boolean noBasicLands = false;

        // Adjust minimum base on number of Advantageous Proclamation or similar cards
        CardPool conspiracies = deck.get(DeckSection.Conspiracy);
        if (conspiracies != null) {
            min -= (5 * conspiracies.countByName(ADVPROCLAMATION));
            // Commented out to remove warnings from the code.
            // noBasicLands = conspiracies.countByName(SOVREALM) > 0;
        }

        if (hasCommander()) {
            byte cmdCI = 0;
            int wildColors = 0;
            if (equals(DeckFormat.Oathbreaker)) { // 1 Oathbreaker and 1 Signature Spell
                PaperCard oathbreaker = deck.getOathbreaker();
                if (oathbreaker == null) {
                    return "is missing an oathbreaker";
                }
                if (deck.getSignatureSpell() == null) {
                    return "is missing a signature spell";
                }
                if (deck.getCommanders().size() > 2) {
                    return "has too many commanders";
                }
                cmdCI = oathbreaker.getRules().getColorIdentity().getColor();
            } else { // 1 Commander or 2 Partner Commanders
                final List<PaperCard> commanders = deck.getCommanders();

                if (commanders.isEmpty()) {
                    return "is missing a commander";
                }

                if (commanders.size() > 2) {
                    return "has too many commanders";
                }

                for (PaperCard pc : commanders) {
                    if (!isLegalCommander(pc.getRules())) {
                        return "has an illegal commander";
                    }
                    cmdCI |= pc.getRules().getColorIdentity().getColor();
                    wildColors += pc.getRules().getAddsWildCardColor() ? 1 : 0;
                }

                // Special check for Partner
                if (commanders.size() == 2) {
                    // Two commander = 98 cards
                    min--;
                    max--;

                    PaperCard a = commanders.get(0);
                    PaperCard b = commanders.get(1);

                    if (!a.getRules().canBePartnerCommanders(b.getRules())) {
                        return "has an illegal commander partnership";
                    }
                }
            }

            final List<PaperCard> erroneousCI = new ArrayList<>();

            Set<String> basicLandNames = new HashSet<>();
            for (final Entry<PaperCard, Integer> cp : deck.get(DeckSection.Main)) {
                // If colourless commander allow one type of basic land
                if (cmdCI == 0 && cp.getKey().getRules().getType().isBasicLand()){
                    basicLandNames.add(cp.getKey().getName());
                    if(basicLandNames.size() < 2){
                        continue;
                    }
                }
                ColorSet missingColors = cp.getKey().getRules().getColorIdentity().getMissingColors(cmdCI);
                if (missingColors.countColors() > 0) {
                    if (missingColors.countColors() <= wildColors) {
                        wildColors -= missingColors.countColors();
                        cmdCI |= missingColors.getColor();
                    } else {
                        erroneousCI.add(cp.getKey());
                    }
                }
            }
            if (deck.has(DeckSection.Sideboard)) {
                for (final Entry<PaperCard, Integer> cp : deck.get(DeckSection.Sideboard)) {
                    if (!cp.getKey().getRules().getColorIdentity().hasNoColorsExcept(cmdCI)) {
                        erroneousCI.add(cp.getKey());
                    }
                }
            }

            if (!erroneousCI.isEmpty()) {
                StringBuilder sb = new StringBuilder("contains one or more cards that do not match the commanders color identity:");

                for (PaperCard cp : erroneousCI) {
                    sb.append("\n").append(cp.getName());
                }

                return sb.toString();
            }
        }

        if (deckSize < min) {
            return TextUtil.concatWithSpace("should have at least", String.valueOf(min), "cards");
        }

        if (deckSize > max) {
            return TextUtil.concatWithSpace("should have no more than", String.valueOf(max), "cards");
        }

        if (cardPoolFilter != null) {
            final List<PaperCard> erroneousCI = new ArrayList<>();
            for (final Entry<PaperCard, Integer> cp : deck.getAllCardsInASinglePool()) {
                if (!cardPoolFilter.test(cp.getKey().getRules())) {
                    erroneousCI.add(cp.getKey());
                }
            }
            if (!erroneousCI.isEmpty()) {
                final StringBuilder sb = new StringBuilder("contains the following illegal cards:\n");

                for (final PaperCard cp : erroneousCI) {
                    sb.append("\n").append(cp.getName());
                }

                return sb.toString();
            }
        }

        if (deck.has(DeckSection.Attractions)) {
            String attractionError = getAttractionDeckConformanceProblem(deck);
            if (attractionError != null)
                return attractionError;
        }

        if (deck.has(DeckSection.Contraptions)) {
            String contraptionError = getContraptionDeckConformanceProblem(deck);
            if (contraptionError != null)
                return contraptionError;
        }

        final int maxCopies = getMaxCardCopies();
        // Must contain no more than 4 of the same card shared among the main deck and sideboard, except
        // basic lands, Shadowborn Apostle, Relentless Rats and Rat Colony.
        // Seven Dwarves can have 7 in the deck. More than 7 in deck + sb is ok in Limited

        final CardPool allCards = deck.getAllCardsInASinglePool(hasCommander());

        // Should group all cards by name, so that different editions of same card are really counted as the same card
        for (final Entry<String, Integer> cp : Aggregates.groupSumBy(allCards, pc -> StaticData.instance().getCommonCards().getName(pc.getName(), true))) {
            IPaperCard simpleCard = StaticData.instance().getCommonCards().getCard(cp.getKey());
            if (simpleCard != null && simpleCard.getRules().isCustom() && !StaticData.instance().allowCustomCardsInDecksConformance())
                return TextUtil.concatWithSpace("contains a Custom Card:", cp.getKey(), "\nPlease Enable Custom Cards in Forge Preferences to use this deck.");
            // Might cause issues since it ignores "Special" Cards
            if (simpleCard == null) {
                simpleCard = StaticData.instance().getVariantCards().getCard(cp.getKey());
                if (simpleCard == null) {
                    return TextUtil.concatWithSpace("contains the nonexisting card", cp.getKey());
                }
            }

            if (canHaveAnyNumberOf(simpleCard)) {
                continue;
            }

            Integer cardCopies = canHaveSpecificNumberInDeck(simpleCard);
            if (cardCopies != null && deck.getMain().countByName(cp.getKey()) > cardCopies) {
                return TextUtil.concatWithSpace("must not contain more than", String.valueOf(cardCopies), "copies of the card", cp.getKey());
            }

            if (cardCopies == null && cp.getValue() > maxCopies) {
                return TextUtil.concatWithSpace("must not contain more than", String.valueOf(maxCopies), "copies of the card", cp.getKey());
            }
        }

        // The sideboard must contain either 0 or 15 cards
        int sideboardSize = deck.has(DeckSection.Sideboard) ? deck.get(DeckSection.Sideboard).countAll() : 0;
        Range<Integer> sbRange = getSideRange();
        if (sbRange != null && sideboardSize > 0 && !sbRange.contains(sideboardSize)) {
            return sbRange.getMinimum().equals(sbRange.getMaximum())
                ? TextUtil.concatWithSpace("must have a sideboard of", String.valueOf(sbRange.getMinimum()), "cards or no sideboard at all")
                : TextUtil.concatWithSpace("must have a sideboard of", String.valueOf(sbRange.getMinimum()), "to", String.valueOf(sbRange.getMaximum()), "cards or no sideboard at all");
        }

        return null;
    }

    public String getAttractionDeckConformanceProblem(Deck deck) {
        CardPool attractionDeck = deck.get(DeckSection.Attractions);
        if (attractionDeck.countAll() < 10)
            return "must contain at least 10 attractions, or none at all";
        for (Entry<PaperCard, Integer> cp : attractionDeck) {
            // Constructed Attraction deck must be singleton
            if (attractionDeck.countByName(cp.getKey()) > 1)
                return TextUtil.concatWithSpace("contains more than 1 copy of the attraction", cp.getKey().getName());
        }
        return null;
    }

    public String getContraptionDeckConformanceProblem(Deck deck) {
        CardPool contraptionDeck = deck.get(DeckSection.Contraptions);
        if (contraptionDeck.countAll() < 15)
            return "must contain at least 15 contraptions, or none at all";
        for (Entry<PaperCard, Integer> cp : contraptionDeck) {
            // Constructed Contraption deck must be singleton
            if (contraptionDeck.countByName(cp.getKey()) > 1)
                return TextUtil.concatWithSpace("contains more than 1 copy of the contraption", cp.getKey().getName());
        }
        return null;
    }

    public static boolean canHaveAnyNumberOf(final IPaperCard iCard) {
        return iCard.getRules().getType().isBasicLand()
            || Iterables.contains(iCard.getRules().getMainPart().getKeywords(),
                "A deck can have any number of cards named CARDNAME.");
    }

    public static Integer canHaveSpecificNumberInDeck(final IPaperCard card) {
        // Ideally, this would be parsed during card parsing and set this value
        return card.getRules().getKeywordMagnitude("DeckLimit");
    }

    public static String getPlaneSectionConformanceProblem(final CardPool planes) {
        // Must contain at least 10 planes/phenomenons, but max 2 phenomenons. Singleton.
        if (planes == null || planes.countAll() < 10) {
            return "should have at least 10 planes";
        }
        int phenoms = 0;
        for (Entry<PaperCard, Integer> cp : planes) {
            if (cp.getKey().getRules().getType().hasType(CardType.CoreType.Phenomenon)) {
                phenoms++;
            }
            if (cp.getValue() > 1) {
                return "must not contain multiple copies of any Plane or Phenomena";
            }
        }
        if (phenoms > 2) {
            return "must not contain more than 2 Phenomena";
        }
        return null;
    }

    public static String getSchemeSectionConformanceProblem(final CardPool schemes) {
        // Must contain at least 20 schemes, max 2 of each.
        if (schemes == null || schemes.countAll() < 20) {
            return "must contain at least 20 schemes";
        }

        for (Entry<PaperCard, Integer> cp : schemes) {
            if (cp.getValue() > 2) {
                return TextUtil.concatWithSpace("must not contain more than 2 copies of any Scheme, but has", String.valueOf(cp.getValue()), "of", TextUtil.enclosedSingleQuote(cp.getKey().getName()));
            }
        }
        return null;
    }

    public IDeckGenPool getCardPool(IDeckGenPool basePool) {
        if (cardPoolFilter == null) {
            if (paperCardPoolFilter == null) {
                return basePool;
            }
            DeckGenPool filteredPool = new DeckGenPool();
            for (PaperCard pc : basePool.getAllCards()) {
                if (paperCardPoolFilter.test(pc)) {
                    filteredPool.add(pc);
                }
            }
            return filteredPool;
        }
        DeckGenPool filteredPool = new DeckGenPool();
        for (PaperCard pc : basePool.getAllCards()) {
            if (cardPoolFilter.test(pc.getRules())) {
                filteredPool.add(pc);
            }
        }
        return filteredPool;
    }

    public void adjustCMCLevels(List<ImmutablePair<FilterCMC, Integer>> cmcLevels) {
        // Not needed by default
    }

    public boolean isLegalCard(PaperCard pc) {
        if (cardPoolFilter == null) {
            if (paperCardPoolFilter == null) {
                return true;
            }
            return paperCardPoolFilter.test(pc);
        }
        return cardPoolFilter.test(pc.getRules());
    }

    public boolean isLegalCommander(CardRules rules) {
        if (cardPoolFilter != null && !cardPoolFilter.test(rules)) {
            return false;
        }
        if (this.equals(DeckFormat.Oathbreaker)) {
            return rules.canBeOathbreaker();
        }
        if (this.equals(DeckFormat.Brawl)) {
            return rules.canBeBrawlCommander();
        }
        if (this.equals(DeckFormat.TinyLeaders)) {
            return rules.canBeTinyLeadersCommander();
        }
        return rules.canBeCommander();
    }

    public Predicate<Deck> isLegalDeckPredicate() {
        return deck -> getDeckConformanceProblem(deck) == null;
    }

    public Predicate<Deck> hasLegalCardsPredicate(boolean enforceDeckLegality) {
        return deck -> {
            if (!enforceDeckLegality)
                return true;
            if (cardPoolFilter != null) {
                for (final Entry<PaperCard, Integer> cp : deck.getAllCardsInASinglePool()) {
                    if (!cardPoolFilter.test(cp.getKey().getRules())) {
                        return false;
                    }
                }
            }
            if (paperCardPoolFilter != null) {
                for (final Entry<PaperCard, Integer> cp : deck.getAllCardsInASinglePool()) {
                    if (!paperCardPoolFilter.test(cp.getKey())) {
                        System.err.println(
                                "Excluding deck: '" + deck +
                                "' Reason: '" + cp.getKey() + "' is not legal."
                        );
                        return false;
                    }
                }
            }
            return true;
        };
    }

    public Predicate<PaperCard> isLegalCardPredicate() {
        return this::isLegalCard;
    }

    public Predicate<PaperCard> isLegalCommanderPredicate() {
        return card -> isLegalCommander(card.getRules());
    }

    public Predicate<PaperCard> isLegalCardForCommanderPredicate(List<PaperCard> commanders) {
        byte cmdCI = 0;
        for (final PaperCard p : commanders) {
            cmdCI |= p.getRules().getColorIdentity().getColor();
        }
        Predicate<CardRules> predicate = CardRulesPredicates.hasColorIdentity(cmdCI);
        if (commanders.size() == 1 && commanders.get(0).getRules().canBePartnerCommander()) {
            // Also show available partners a commander can have a partner.
            // 702.124g If a legendary card has more than one partner ability, you may choose which one to use when designating your commander, but you can’t use both.
            // Notably, no partner ability or combination of partner abilities can ever let a player have more than two commanders.
            predicate = predicate.or(CardRulesPredicates.canBePartnerCommanderWith(commanders.get(0).getRules()));
        }
        return PaperCardPredicates.fromRules(predicate);
    }
}

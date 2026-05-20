package cn.jason31416.betterresidence.handler;

import cn.jason31416.betterresidence.core.Claim;
import cn.jason31416.betterresidence.core.ClaimGroup;
import cn.jason31416.betterresidence.core.ClaimManager;
import cn.jason31416.betterresidence.core.DefaultClaimGroupRegistry;
import cn.jason31416.planetlib.wrapper.SimplePlayer;
import com.destroystokyo.paper.event.brigadier.AsyncPlayerSendSuggestionsEvent;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CommandSuggestionListener implements Listener {
    private static final Set<String> ROOT_COMMANDS = Set.of("betterresidence", "res", "betterres", "bres");

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerSendSuggestions(AsyncPlayerSendSuggestionsEvent event) {
        if (event.isAsynchronous() || !isGroupArgument(event.getBuffer())) {
            return;
        }

        Claim claim = ClaimManager.findClaimAt(SimplePlayer.of(event.getPlayer()).getLocation());
        if (claim == null) {
            return;
        }

        Map<String, Integer> groupWeights = getGroupWeights(claim);
        Suggestions suggestions = event.getSuggestions();
        List<Suggestion> orderedSuggestions = reorderSuggestions(suggestions.getList(), groupWeights);
        if (orderedSuggestions == null) {
            return;
        }

        event.setSuggestions(new Suggestions(suggestions.getRange(), orderedSuggestions));
    }

    private boolean isGroupArgument(String buffer) {
        List<String> args = splitCommand(buffer);
        if (args.isEmpty() || !ROOT_COMMANDS.contains(args.get(0).toLowerCase(Locale.ROOT))) {
            return false;
        }

        if (args.size() == 4 && args.get(1).equalsIgnoreCase("trust")) {
            return true;
        }
        return args.size() == 4 && args.get(1).equalsIgnoreCase("set");
    }

    private List<String> splitCommand(String buffer) {
        String command = buffer.startsWith("/") ? buffer.substring(1) : buffer;
        if (command.isBlank()) {
            return List.of();
        }
        return List.of(command.split(" ", -1));
    }

    private Map<String, Integer> getGroupWeights(Claim claim) {
        Map<String, Integer> groupWeights = new HashMap<>();
        groupWeights.put(DefaultClaimGroupRegistry.getVisitorName(), DefaultClaimGroupRegistry.VISITOR_WEIGHT);
        for (ClaimGroup group : claim.getClaimGroups()) {
            groupWeights.put(group.name(), group.weight());
        }
        return groupWeights;
    }

    private List<Suggestion> reorderSuggestions(List<Suggestion> suggestions, Map<String, Integer> groupWeights) {
        List<Suggestion> weightedSuggestions = new ArrayList<>();
        List<Suggestion> otherSuggestions = new ArrayList<>();
        for (Suggestion suggestion : suggestions) {
            if (groupWeights.containsKey(suggestion.getText())) {
                weightedSuggestions.add(suggestion);
            } else {
                otherSuggestions.add(suggestion);
            }
        }

        if (weightedSuggestions.isEmpty()) {
            return null;
        }

        weightedSuggestions.sort(Comparator
                .comparingInt((Suggestion suggestion) -> groupWeights.get(suggestion.getText()))
                .reversed());
        weightedSuggestions.addAll(otherSuggestions);
        return weightedSuggestions;
    }
}

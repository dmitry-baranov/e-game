package ru.itis.diploma.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.itis.diploma.dto.StrategyPlan;
import ru.itis.diploma.security.details.AccountUserDetails;
import ru.itis.diploma.service.StrategyService;
import ru.itis.diploma.service.StrategySnapshotService;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
@RequestMapping("/game/{gameId}/strategies")
@PreAuthorize("hasAuthority('USER')")
public class StrategyController {
    private final StrategyService service;
    private final StrategySnapshotService snapshots;

    private long account(AccountUserDetails user) { return user.getAccount().getId(); }

    private void page(Long gameId, AccountUserDetails user, Model model) {
        long id = account(user);
        model.addAttribute("gameId", gameId);
        model.addAttribute("snapshot", snapshots.snapshot(gameId, id));
        model.addAttribute("owner", snapshots.owner(gameId, id));
        model.addAttribute("plans", service.plans(gameId, id));
        model.addAttribute("history", service.history(gameId, id));
        model.addAttribute("requestKey", UUID.randomUUID().toString());
    }

    @GetMapping
    public String index(@PathVariable Long gameId, @RequestParam(required = false) Long planId,
                        @AuthenticationPrincipal AccountUserDetails user, Model model) {
        page(gameId, user, model);
        model.addAttribute("plan", planId == null ? new StrategyPlan() : service.plan(gameId, account(user), planId));
        return "strategies";
    }

    @PostMapping("/recommend")
    public String recommend(@PathVariable Long gameId, @RequestParam String requestKey,
                            @AuthenticationPrincipal AccountUserDetails user, Model model) {
        page(gameId, user, model);
        model.addAttribute("plan", new StrategyPlan());
        model.addAttribute("advice", service.recommend(gameId, account(user), requestKey, null));
        return "strategies";
    }

    @GetMapping("/history/{historyId}")
    public String historyEntry(@PathVariable Long gameId, @PathVariable Long historyId,
                               @AuthenticationPrincipal AccountUserDetails user, Model model) {
        page(gameId, user, model);
        model.addAttribute("plan", new StrategyPlan());
        model.addAttribute("advice", service.historicalAdvice(gameId, account(user), historyId));
        model.addAttribute("historical", true);
        return "strategies";
    }

    @PostMapping("/compare")
    public String compare(@PathVariable Long gameId, StrategyPlan plan,
                          @AuthenticationPrincipal AccountUserDetails user, Model model) {
        page(gameId, user, model);
        model.addAttribute("plan", plan);
        model.addAttribute("advice", service.recommend(gameId, account(user), UUID.randomUUID().toString(), plan));
        return "strategies";
    }

    @PostMapping("/save")
    public String save(@PathVariable Long gameId, StrategyPlan plan,
                       @AuthenticationPrincipal AccountUserDetails user) {
        Long id = service.save(gameId, account(user), plan);
        return "redirect:/game/" + gameId + "/strategies?planId=" + id;
    }

    @PostMapping("/delete/{planId}")
    public String delete(@PathVariable Long gameId, @PathVariable Long planId,
                         @AuthenticationPrincipal AccountUserDetails user) {
        service.delete(gameId, account(user), planId);
        return "redirect:/game/" + gameId + "/strategies";
    }

    @PostMapping("/select")
    public String select(@PathVariable Long gameId, @RequestParam String course,
                         @RequestParam(required = false) Long historyId,
                         @AuthenticationPrincipal AccountUserDetails user) {
        service.select(gameId, account(user), course, historyId);
        return "redirect:/game/" + gameId + "/strategies";
    }

    @PostMapping("/history-preference")
    public String history(@PathVariable Long gameId, @RequestParam(defaultValue = "false") boolean enabled,
                          @AuthenticationPrincipal AccountUserDetails user) {
        service.historyPreference(gameId, account(user), enabled);
        return "redirect:/game/" + gameId + "/strategies";
    }

    @PostMapping("/training-preference")
    public String training(@PathVariable Long gameId, @RequestParam(defaultValue = "false") boolean enabled,
                           @AuthenticationPrincipal AccountUserDetails user) {
        service.trainingPreference(gameId, account(user), enabled);
        return "redirect:/game/" + gameId + "/strategies";
    }
}

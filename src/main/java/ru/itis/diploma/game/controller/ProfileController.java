package ru.itis.diploma.game.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.itis.diploma.game.dto.AccountDto;
import ru.itis.diploma.game.dto.EditAccount;
import ru.itis.diploma.game.model.Account;
import ru.itis.diploma.security.details.AccountUserDetails;
import ru.itis.diploma.game.service.AccountService;

@Controller
@RequiredArgsConstructor
@RequestMapping("/profile")
public class ProfileController {

    private final AccountService accountService;
    @Value("${experiment.enabled:false}") private boolean experimentsEnabled;

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public String getProfilePage(@AuthenticationPrincipal AccountUserDetails userDetails, Model model) {
        AccountDto account = accountService.getByEmail(userDetails.getUsername());
        model.addAttribute("account", account);
        if (Account.Role.ADMIN.equals(userDetails.getAccount().getRole())) {
            model.addAttribute("botExperimentsEnabled", experimentsEnabled);
            return "profile_admin";
        }
        return "profile";
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/edit")
    public String getEditProfilePage() {
        return "edit_profile";
    }


    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public String updateAccountInfo(@AuthenticationPrincipal AccountUserDetails userDetails, EditAccount account) {
        accountService.updateAccountInfo(userDetails.getAccount().getId(), account);
        return "redirect:/profile";
    }
}

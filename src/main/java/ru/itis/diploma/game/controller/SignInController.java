package ru.itis.diploma.game.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/sign-in")
public class SignInController {

    @GetMapping
    @PreAuthorize("permitAll()")
    public String getSignInPage(Authentication authentication) {
        if (authentication != null) {
            return "redirect:/";
        }

        return "sign_in";
    }
}

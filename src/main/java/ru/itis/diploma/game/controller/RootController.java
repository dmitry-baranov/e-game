package ru.itis.diploma.game.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/")
public class RootController {

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public String getRootPage() {
        return "redirect:/profile";
    }

}

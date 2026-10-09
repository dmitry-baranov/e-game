package ru.itis.diploma.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.itis.diploma.dto.SignUpForm;
import ru.itis.diploma.service.SignUpService;

import jakarta.validation.Valid;
@RequiredArgsConstructor
@Controller
@RequestMapping("/sign-up")
public class SignUpController {

    private final SignUpService signUpService;

    @GetMapping
    @PreAuthorize("permitAll()")
    public String getSignUpPage(Authentication authentication, Model model) {

        if (authentication != null) {
            return "redirect:/";
        }

        model.addAttribute("signUpForm", new SignUpForm());
        return "sign_up";
    }

    @PostMapping
    @PreAuthorize("permitAll()")
    public String signUp(@Valid SignUpForm form, BindingResult result, Model model) {
        if (result.hasErrors()) {
            model.addAttribute("signUpForm", form);
            return "sign_up";
        }
        signUpService.signUp(form);
        return "redirect:/sign-in";
    }
}

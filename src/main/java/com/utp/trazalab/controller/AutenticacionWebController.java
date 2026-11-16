package com.utp.trazalab.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AutenticacionWebController {
    @GetMapping({"/login", "/usuarios/login"})
    public String login() {
        return "usuarios/login";
    }
}

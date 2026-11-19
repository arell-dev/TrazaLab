package com.utp.trazalab.usuarios.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginController {
    @GetMapping({"/login", "/usuarios/login"})
    public String login() {
        return "usuarios/login";
    }
}

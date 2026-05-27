package com.streetcar.controller;

import com.streetcar.model.Usuario;
import com.streetcar.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private UsuarioService service;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        return service.autenticar(body.get("email"), body.get("senha"))
                .map(u -> ResponseEntity.ok((Object) Map.of(
                        "id",       u.getId(),
                        "nome",     u.getNome(),
                        "email",    u.getEmail(),
                        "telefone", u.getTelefone() != null ? u.getTelefone() : "",
                        "perfil",   u.getPerfil()
                )))
                .orElse(ResponseEntity.status(401).body(Map.of("erro", "Email ou senha inválidos")));
    }

    @PostMapping("/cadastro")
    public ResponseEntity<?> cadastrar(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String nome  = body.get("nome");
        String senha = body.get("senha");

        if (nome == null || nome.isBlank())   return ResponseEntity.badRequest().body(Map.of("erro", "Nome obrigatório"));
        if (email == null || email.isBlank())  return ResponseEntity.badRequest().body(Map.of("erro", "Email obrigatório"));
        if (senha == null || senha.isBlank())  return ResponseEntity.badRequest().body(Map.of("erro", "Senha obrigatória"));
        if (senha.length() < 4)               return ResponseEntity.badRequest().body(Map.of("erro", "Senha deve ter ao menos 4 caracteres"));

        if (service.existeEmail(email))
            return ResponseEntity.badRequest().body(Map.of("erro", "Email já cadastrado"));

        // FIX 1: usa service.criar() que já encripta a senha via BCrypt
        Usuario u = new Usuario();
        u.setNome(nome);
        u.setEmail(email);
        u.setSenha(senha); // service.criar() encripta antes de salvar
        u.setTelefone(body.getOrDefault("telefone", ""));
        u.setPerfil("CLIENTE");

        service.criar(u);
        return ResponseEntity.ok(Map.of("mensagem", "Cadastro realizado com sucesso!"));
    }
}

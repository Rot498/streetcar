package com.streetcar.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streetcar.model.*;
import com.streetcar.repository.*;
import com.streetcar.service.UsuarioService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@DisplayName("LocacaoController — testes de integração")
class LocacaoControllerTest {

    @Autowired MockMvc mvc;
    @Autowired LocacaoRepository locacaoRepo;
    @Autowired VeiculoRepository veiculoRepo;
    @Autowired UsuarioService usuarioService;
    @Autowired ObjectMapper mapper;

    private Usuario cliente;
    private Veiculo veiculo;

    @BeforeEach
    void setup() {
        Usuario u = new Usuario();
        u.setNome("CLI"); u.setEmail("cli@t.com"); u.setSenha("pw"); u.setTelefone(""); u.setPerfil("CLIENTE");
        cliente = usuarioService.criar(u);
        veiculo = veiculoRepo.save(new Veiculo(null, "Civic", "LOC01", 2023, 180.0, "Disponível"));
    }

    /** Cria e salva uma locação com todos os campos obrigatórios preenchidos */
    private Locacao salvarLocacao() {
        Locacao loc = new Locacao();
        loc.setCliente(cliente); loc.setVeiculo(veiculo);
        loc.setDataInicio(LocalDate.now()); loc.setDataFim(LocalDate.now().plusDays(3));
        loc.setStatus("Ativa"); loc.setValorTotal(540.0);
        loc.setEntregaConfirmada(false);
        return locacaoRepo.save(loc);
    }

    @Test @DisplayName("GET /api/locacoes → retorna lista")
    void listar() throws Exception {
        salvarLocacao();
        mvc.perform(get("/api/locacoes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test @DisplayName("GET /api/locacoes/cliente/{id} → filtra por cliente")
    void porCliente() throws Exception {
        salvarLocacao();
        mvc.perform(get("/api/locacoes/cliente/" + cliente.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cliente.id", is(cliente.getId().intValue())));
    }

    @Test @DisplayName("GET /api/locacoes/{id} → encontrado")
    void buscar_encontrado() throws Exception {
        Locacao loc = salvarLocacao();
        mvc.perform(get("/api/locacoes/" + loc.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("Ativa")));
    }

    @Test @DisplayName("GET /api/locacoes/{id} → não encontrado → 404")
    void buscar_404() throws Exception {
        mvc.perform(get("/api/locacoes/99999")).andExpect(status().isNotFound());
    }

    @Test @DisplayName("POST /api/locacoes → cria, status Ativa, veículo vira Alugado")
    void criar() throws Exception {
        Locacao loc = new Locacao();
        loc.setCliente(cliente); loc.setVeiculo(veiculo);
        loc.setDataInicio(LocalDate.now()); loc.setDataFim(LocalDate.now().plusDays(2));
        loc.setValorTotal(360.0);

        mvc.perform(post("/api/locacoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(loc)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("Ativa")));

        String statusVeiculo = veiculoRepo.findById(veiculo.getId()).orElseThrow().getStatus();
        org.junit.jupiter.api.Assertions.assertEquals("Alugado", statusVeiculo);
    }

    @Test @DisplayName("POST /api/locacoes → veículo não disponível → 409")
    void criar_veiculoIndisponivel() throws Exception {
        veiculo.setStatus("Manutenção");
        veiculoRepo.save(veiculo);

        Locacao loc = new Locacao();
        loc.setCliente(cliente); loc.setVeiculo(veiculo);
        loc.setDataInicio(LocalDate.now()); loc.setDataFim(LocalDate.now().plusDays(2));
        loc.setValorTotal(360.0);

        mvc.perform(post("/api/locacoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(loc)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.erro", notNullValue()));
    }

    @Test @DisplayName("POST /api/locacoes → conflito de datas → 409")
    void criar_conflitoData() throws Exception {
        salvarLocacao(); // ocupa veiculo no período

        Locacao loc = new Locacao();
        loc.setCliente(cliente); loc.setVeiculo(veiculo);
        loc.setDataInicio(LocalDate.now()); loc.setDataFim(LocalDate.now().plusDays(1));
        loc.setValorTotal(180.0);

        mvc.perform(post("/api/locacoes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(loc)))
                .andExpect(status().isConflict());
    }

    @Test @DisplayName("POST /api/locacoes/{id}/devolucao → encerra e libera veículo")
    void devolucao() throws Exception {
        // Precisa do veiculo como Alugado para devolucao funcionar
        veiculo.setStatus("Alugado");
        veiculoRepo.save(veiculo);
        Locacao loc = salvarLocacao();

        String body = String.format(
            "{\"danos\":\"\",\"nivelCombustivel\":\"Cheio\",\"dataDevReal\":\"%s\"}",
            LocalDate.now().toString()
        );

        mvc.perform(post("/api/locacoes/" + loc.getId() + "/devolucao")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("Encerrada")));

        String st = veiculoRepo.findById(veiculo.getId()).orElseThrow().getStatus();
        org.junit.jupiter.api.Assertions.assertEquals("Disponível", st);
    }

    @Test @DisplayName("PUT /api/locacoes/{id} → encerrar libera veículo")
    void encerrar_liberaVeiculo() throws Exception {
        Locacao loc = salvarLocacao();
        loc.setStatus("Encerrada");

        mvc.perform(put("/api/locacoes/" + loc.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(loc)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("Encerrada")));

        String st = veiculoRepo.findById(veiculo.getId()).orElseThrow().getStatus();
        org.junit.jupiter.api.Assertions.assertEquals("Disponível", st);
    }

    @Test @DisplayName("PUT /api/locacoes/{id} → não encontrado → 404")
    void atualizar_404() throws Exception {
        Locacao loc = new Locacao();
        loc.setCliente(cliente); loc.setVeiculo(veiculo);
        loc.setDataInicio(LocalDate.now()); loc.setDataFim(LocalDate.now().plusDays(1));
        loc.setStatus("Ativa"); loc.setValorTotal(180.0);
        mvc.perform(put("/api/locacoes/99999")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(loc)))
                .andExpect(status().isNotFound());
    }

    @Test @DisplayName("DELETE /api/locacoes/{id} → remove → 204")
    void deletar() throws Exception {
        Locacao loc = salvarLocacao();
        mvc.perform(delete("/api/locacoes/" + loc.getId())).andExpect(status().isNoContent());
    }

    @Test @DisplayName("DELETE /api/locacoes/{id} → não encontrado → 404")
    void deletar_404() throws Exception {
        mvc.perform(delete("/api/locacoes/99999")).andExpect(status().isNotFound());
    }
}

package br.ufpb.dsc.caladrius.controller;

import br.ufpb.dsc.caladrius.repository.CidadeRepository;
import br.ufpb.dsc.caladrius.repository.UsuarioRepository;
import br.ufpb.dsc.caladrius.repository.VeiculoRepository;
import br.ufpb.dsc.caladrius.repository.ViagemRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Painel inicial (dashboard) do CALADRIUS.
 *
 * <p>Exibe um resumo com os totais de cada cadastro. As ações de gestão na tela
 * só aparecem para o gerente (controle por papel feito no template via
 * {@code sec:authorize}).
 */
@Controller
public class HomeController {

    private final UsuarioRepository usuarioRepository;
    private final VeiculoRepository veiculoRepository;
    private final CidadeRepository cidadeRepository;
    private final ViagemRepository viagemRepository;

    public HomeController(UsuarioRepository usuarioRepository,
                         VeiculoRepository veiculoRepository,
                         CidadeRepository cidadeRepository,
                         ViagemRepository viagemRepository) {
        this.usuarioRepository = usuarioRepository;
        this.veiculoRepository = veiculoRepository;
        this.cidadeRepository = cidadeRepository;
        this.viagemRepository = viagemRepository;
    }

    @GetMapping("/")
    public String inicio(Model model) {
        model.addAttribute("titulo", "Início");
        model.addAttribute("totalUsuarios", usuarioRepository.countByRemovidoEmIsNull());
        model.addAttribute("totalVeiculos", veiculoRepository.countByRemovidoEmIsNull());
        model.addAttribute("totalCidades", cidadeRepository.count());
        model.addAttribute("totalViagens", viagemRepository.count());
        return "inicio";
    }
}

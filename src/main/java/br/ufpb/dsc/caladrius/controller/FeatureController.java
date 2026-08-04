package br.ufpb.dsc.caladrius.controller;

import br.ufpb.dsc.caladrius.exception.RegraNegocioException;
import br.ufpb.dsc.caladrius.service.ChaveFeature;
import br.ufpb.dsc.caladrius.service.FeatureFlagService;
import br.ufpb.dsc.caladrius.service.MunicipioService;
import br.ufpb.dsc.caladrius.service.ParametroSistema;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;
import java.util.UUID;

/**
 * Área de <strong>feature toggle</strong> do SYSADMIN (SPEC-PLT-01, FR-FLG-01/02/03):
 *
 * <ul>
 *   <li>{@code /admin/features} — liga/desliga as flags globais (bot do WhatsApp,
 *       modo de manutenção) e edita os parâmetros de negócio;</li>
 *   <li>{@code /admin/municipios} — marca a adesão de cada município ao (futuro)
 *       fluxo de pagamento (<em>entitlement</em>, RN-FLG-05).</li>
 * </ul>
 *
 * <p>Sem {@code @RequestMapping} de classe: as duas telas vivem sob {@code /admin}
 * (restrito ao SYSADMIN no {@code SecurityConfig}) mas têm caminhos próprios. Os
 * interruptores usam HTMX e devolvem o bloco atualizado — o token CSRF vai no
 * {@code hx-headers} do container (estas rotas <strong>não</strong> estão na lista de
 * exceções de CSRF, por serem administrativas).
 */
@Controller
public class FeatureController {

    private final FeatureFlagService featureFlags;
    private final MunicipioService municipioService;

    public FeatureController(FeatureFlagService featureFlags, MunicipioService municipioService) {
        this.featureFlags = featureFlags;
        this.municipioService = municipioService;
    }

    // ------------------------------------------------------------ flags globais

    @GetMapping("/admin/features")
    public String features(Model model) {
        model.addAttribute("titulo", "Funcionalidades");
        carregarFlags(model);
        model.addAttribute("parametros", featureFlags.estadoDosParametros());
        model.addAttribute("municipiosAderidos", municipioService.listarAderidos());
        return "admin/features";
    }

    /** Alterna uma flag e devolve o bloco de flags atualizado (HTMX). */
    @PostMapping("/admin/features/{chave}")
    public String alternar(@PathVariable ChaveFeature chave,
                           @RequestParam(name = "ativo", defaultValue = "false") boolean ativo,
                           Model model) {
        featureFlags.definir(chave, ativo);
        carregarFlags(model);
        return "admin/fragments/flags :: lista";
    }

    /** Salva os parâmetros de negócio (validação de intervalo no serviço — RN-FLG-06). */
    @PostMapping("/admin/features/parametros")
    public String salvarParametros(@RequestParam Map<String, String> valores,
                                   RedirectAttributes redirect) {
        for (ParametroSistema parametro : ParametroSistema.values()) {
            String informado = valores.get(parametro.name());
            if (informado == null || informado.isBlank()) {
                continue;
            }
            try {
                featureFlags.definir(parametro, Integer.parseInt(informado.trim()));
            } catch (NumberFormatException e) {
                redirect.addFlashAttribute("erro",
                        parametro.getRotulo() + ": informe um número inteiro.");
                return "redirect:/admin/features";
            } catch (RegraNegocioException e) {
                // Fora do intervalo (RN-FLG-06): a escrita é recusada e o valor anterior fica.
                redirect.addFlashAttribute("erro", e.getMessage());
                return "redirect:/admin/features";
            }
        }
        redirect.addFlashAttribute("sucesso", "Parâmetros salvos — valem na próxima requisição.");
        return "redirect:/admin/features";
    }

    // ------------------------------------------- entitlement por município (V15)

    @GetMapping("/admin/municipios")
    public String municipios(@RequestParam(name = "busca", required = false, defaultValue = "") String busca,
                             Model model) {
        model.addAttribute("titulo", "Adesão ao pagamento");
        carregarMunicipios(busca, model);
        return "admin/municipios";
    }

    /** Marca/desmarca a adesão e devolve a tabela atualizada (HTMX). */
    @PostMapping("/admin/municipios/{id}")
    public String alternarMunicipio(@PathVariable UUID id,
                                    @RequestParam(name = "habilitado", defaultValue = "false") boolean habilitado,
                                    @RequestParam(name = "busca", required = false, defaultValue = "") String busca,
                                    Model model) {
        municipioService.definirPagamentoHabilitado(id, habilitado);
        carregarMunicipios(busca, model);
        return "admin/fragments/municipios :: tabela";
    }

    // ----------------------------------------------------------------- apoio

    private void carregarFlags(Model model) {
        model.addAttribute("flags", featureFlags.estadoDasFlags());
    }

    private void carregarMunicipios(String busca, Model model) {
        model.addAttribute("busca", busca);
        model.addAttribute("municipios", municipioService.listar(busca));
    }
}

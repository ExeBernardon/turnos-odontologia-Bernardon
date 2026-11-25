package ar.turnos.odontologia.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.turnos.odontologia.auth.JwtService.TokenEmitido;
import ar.turnos.odontologia.auth.dto.LoginRequest;
import ar.turnos.odontologia.auth.dto.LoginResponse;
import ar.turnos.odontologia.auth.dto.UsuarioResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String TIPO_BEARER = "Bearer";

    private final AuthService auth;
    private final JwtService jwt;

    public AuthController(AuthService auth, JwtService jwt) {
        this.auth = auth;
        this.jwt = jwt;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest body) {
        OdontologoPrincipal principal = auth.autenticar(body.email(), body.password());
        TokenEmitido emitido = jwt.emitir(principal.getEmail());
        return ResponseEntity.ok(new LoginResponse(emitido.token(), TIPO_BEARER, emitido.expiraEn(),
                principal.getNombre()));
    }

    @GetMapping("/me")
    public UsuarioResponse me(@AuthenticationPrincipal OdontologoPrincipal principal) {
        return new UsuarioResponse(principal.getId(), principal.getNombre(), principal.getEmail());
    }
}

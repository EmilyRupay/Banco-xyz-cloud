package cl.duoc.banco.bff.atm;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Segundo factor del canal ATM: ademas del token OAuth2 (scope "atm"), cada peticion debe venir de un terminal
 * registrado (header X-ATM-ID). Todo acceso queda en el log de auditoria con el id del terminal.
 */
public class AtmTerminalFilter extends OncePerRequestFilter {

    private static final Logger audit = LoggerFactory.getLogger("AUDITORIA_ATM");
    public static final String HEADER = "X-ATM-ID";

    private final AtmProperties props;

    public AtmTerminalFilter(AtmProperties props) {
        this.props = props;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/atm/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String terminal = request.getHeader(HEADER);
        if (terminal == null || props.terminales() == null || !props.terminales().contains(terminal)) {
            audit.warn("RECHAZADO terminal={} {} {} ip={}", terminal, request.getMethod(), request.getRequestURI(),
                    request.getRemoteAddr());
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"error\":\"Terminal no autorizado\"}");
            return;
        }
        MDC.put("atm", terminal);
        try {
            audit.info("terminal={} {} {}", terminal, request.getMethod(), request.getRequestURI());
            chain.doFilter(request, response);
            audit.info("terminal={} {} {} -> {}", terminal, request.getMethod(), request.getRequestURI(),
                    response.getStatus());
        } finally {
            MDC.remove("atm");
        }
    }
}

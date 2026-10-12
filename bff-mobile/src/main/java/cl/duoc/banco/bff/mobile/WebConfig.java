package cl.duoc.banco.bff.mobile;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

@Configuration
public class WebConfig {

    /** Calcula un ETag del cuerpo: si el movil envia If-None-Match con el mismo valor recibe 304 (sin datos). */
    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> etagFilter() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> registro = new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        registro.addUrlPatterns("/mobile/*");
        return registro;
    }
}

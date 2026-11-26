package ca.vinaitsolution.vinasign.commun.infrastructure.web;

import ca.vinaitsolution.vinasign.commun.infrastructure.VinaSignProprietes;
import ca.vinaitsolution.vinasign.commun.application.port.ContexteAccesCourant;
import ca.vinaitsolution.vinasign.commun.application.port.ControleAcces;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.CrossOriginOpenerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.CrossOriginResourcePolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Refus par défaut : toute route exige une authentification, sauf la courte liste publique ci-dessous, chacune motivée
 * dans securite/donnees/surface-publique.json et éprouvée par INT-01 sur tout l'inventaire OpenAPI. L'authentification
 * est un jeton porteur émis par le serveur d'autorisation (VS-012) : sans état ni cookie, la protection CSRF ne
 * s'applique donc pas. Le serveur d'autorisation a sa propre chaîne, évaluée avant celle-ci.
 */
@Configuration
@EnableMethodSecurity
public class SecuriteConfiguration {

    @Bean
    @Order(2)
    SecurityFilterChain chaine(HttpSecurity http, FiltreControlesAcces controles, FiltreLimiteDebit debit,
                               AuthenticationManagerResolver<HttpServletRequest> emetteurs,
                               List<FiltreAuthentificationComplementaire> complementaires) throws Exception {
        for (FiltreAuthentificationComplementaire f : complementaires) {
            http.addFilterBefore(f, BearerTokenAuthenticationFilter.class);
        }
        return http
                .csrf(c -> c.disable())
                .cors(c -> { })
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.GET, "/api/v1/referentiels", "/api/v1/referentiels/*").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/v3/api-docs", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/depots/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/signataire/ouverture").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/signataire/integration/ancetres").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/verifications").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/availability").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/verification-identite/rappel").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/billing/events").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/signup", "/api/v1/signup/confirmation", "/api/v1/signup/consultation",
                                "/api/v1/signup/domain-verification").permitAll()
                        .requestMatchers("/api/v1/signataire/**").hasAuthority("ROLE_signataire")
                        .anyRequest().authenticated())
                .headers(h -> h
                        .contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .frameOptions(f -> f.deny())
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .crossOriginOpenerPolicy(c -> c.policy(CrossOriginOpenerPolicyHeaderWriter.CrossOriginOpenerPolicy.SAME_ORIGIN))
                        .crossOriginResourcePolicy(c -> c.policy(CrossOriginResourcePolicyHeaderWriter.CrossOriginResourcePolicy.SAME_ORIGIN))
                        .permissionsPolicyHeader(p -> p.policy("camera=(), microphone=(), geolocation=()")))
                .oauth2ResourceServer(o -> o.authenticationManagerResolver(emetteurs))
                .addFilterAfter(controles, BearerTokenAuthenticationFilter.class)
                .addFilterAfter(debit, FiltreControlesAcces.class)
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .build();
    }

    @Bean
    FiltreControlesAcces filtreControlesAcces(ContexteAccesCourant contexte, List<ControleAcces> controles, ObjectMapper json) {
        return new FiltreControlesAcces(contexte, controles, json);
    }

    /** Le filtre est placé dans la chaîne de sécurité seulement, pas aussi dans la chaîne des servlets. */
    @Bean
    FilterRegistrationBean<FiltreControlesAcces> filtreHorsServlets(FiltreControlesAcces filtre) {
        FilterRegistrationBean<FiltreControlesAcces> inscription = new FilterRegistrationBean<>(filtre);
        inscription.setEnabled(false);
        return inscription;
    }

    /** Origines autorisées tirées de la configuration (portail, signataire) ; aucune par défaut. Nom imposé par Spring Security. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(VinaSignProprietes proprietes) {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(proprietes.originesAutorisees());
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        c.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "Accept-Language"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", c);
        return source;
    }
}

package fr.siamois.ui.config;

import fr.siamois.infrastructure.database.repositories.person.PersonRepository;
import fr.siamois.ui.bean.SessionSettingsBean;
import fr.siamois.ui.config.handler.LoginSuccessHandler;
import fr.siamois.ui.config.security.ApiUnauthorizedJsonWriter;
import fr.siamois.ui.config.security.UserInfoContextFilter;
import fr.siamois.ui.config.security.jwt.JwtAuthenticationFilter;
import fr.siamois.ui.config.security.jwt.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.configurers.CorsConfigurer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Configuration class for the security of the application.
 * @author Julien Linget
 */
@Slf4j
@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    public static final String LOGIN = "/login";

    /**
     * Le filtre JWT ne doit pas être enregistré comme filtre Servlet global (sinon il s’applique à {@code /}, JSF, etc.).
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService, PersonRepository personRepository) {
        return new JwtAuthenticationFilter(jwtService, personRepository);
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter jwtAuthenticationFilter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(jwtAuthenticationFilter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Swagger / OpenAPI : accès anonyme, sans redirection vers le formulaire web (SpringDoc + WebJars).
     * <p>
     * CSRF désactivé : documentation en lecture seule, sans authentification par cookie de session.
     */
    @Bean
    @Order(0)
    public SecurityFilterChain documentationSecurityFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher(
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/webjars/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrfDisabled())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED));
        return http.build();
    }

    /**
     * API REST v1 : JWT Bearer (stateless), sans session ni formulaire.
     * <p>
     * CSRF désactivé volontairement : l'authentification repose sur le header {@code Authorization}
     * (Bearer JWT), pas sur un cookie de session. Le filtre web ({@link #webSecurityFilterChain})
     * conserve la protection CSRF pour l'application JSF (formulaires avec jeton {@code _csrf}).
     */
    /**
     * Origines autorisées à appeler l'API v1 depuis un navigateur, séparées par des virgules.
     * Vide par défaut : en production le SPA est servi par le même hôte que l'API, et en
     * développement il passe par le proxy Vite — dans les deux cas la requête est en même origine
     * et aucun CORS n'est nécessaire. Ne renseigner que pour un front servi depuis une autre origine.
     */
    @Value("${siamois.api.cors.allowed-origins:}")
    private String[] apiAllowedOrigins;

    /**
     * L'API v1 s'authentifie par en-tête {@code Authorization}, jamais par cookie : les identifiants
     * de navigateur ne sont donc pas autorisés, et les origines sont listées explicitement — jamais
     * de {@code *}, qui exposerait l'API à n'importe quel site.
     */
    private Customizer<CorsConfigurer<HttpSecurity>> apiCors() {
        if (apiAllowedOrigins == null || apiAllowedOrigins.length == 0) {
            return AbstractHttpConfigurer::disable;
        }
        List<String> origins = Arrays.stream(apiAllowedOrigins)
                .map(String::trim)
                .filter(origin -> !origin.isEmpty() && !"*".equals(origin))
                .toList();
        if (origins.isEmpty()) {
            return AbstractHttpConfigurer::disable;
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT_LANGUAGE));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(Duration.ofMinutes(30));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/v1/**", configuration);
        log.info("CORS activé sur /api/v1/** pour les origines {}", origins);
        return cors -> cors.configurationSource(source);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain apiV1SecurityFilterChain(HttpSecurity http,
                                                        JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        http.securityMatcher("/api/v1/**")
                .cors(apiCors())
                .csrf(csrfDisabled())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/login").permitAll()
                        // Appelés avant qu'un jeton existe : libellés de l'écran de connexion et
                        // inscription sur invitation. Routes listées une par une — un motif large sur
                        // /api/v1/auth/** ouvrirait aussi /api/v1/auth/me.
                        .requestMatchers("/api/v1/i18n/languages", "/api/v1/i18n/messages").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/invitations/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/invitations/*").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, authException) ->
                        ApiUnauthorizedJsonWriter.write(response, "Authentication required")));

        return http.build();
    }

    /**
     * Lie le {@code UserInfo} de session au thread de la requête JSF. Comme le filtre JWT, il ne doit pas
     * être enregistré comme filtre Servlet global : il n'a de sens que dans la chaîne web.
     */
    @Bean
    public UserInfoContextFilter userInfoContextFilter(SessionSettingsBean sessionSettingsBean) {
        return new UserInfoContextFilter(sessionSettingsBean);
    }

    @Bean
    public FilterRegistrationBean<UserInfoContextFilter> userInfoContextFilterRegistration(
            UserInfoContextFilter userInfoContextFilter) {
        FilterRegistrationBean<UserInfoContextFilter> registration = new FilterRegistrationBean<>(userInfoContextFilter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Application web (JSF), formulaire de connexion et sessions HTTP.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain webSecurityFilterChain(HttpSecurity http,
                                                      LoginSuccessHandler loginSuccessHandler,
                                                      UserInfoContextFilter userInfoContextFilter) throws Exception {
        http.authorizeHttpRequests(requests -> requests
                .requestMatchers("/", "/index.xhtml").permitAll()
                .requestMatchers(LOGIN, "/pages/login/login.xhtml", "/pages/login/register.xhtml").permitAll()
                .requestMatchers("/static/**").permitAll()
                .requestMatchers("/robots.txt").permitAll()
                .requestMatchers("/jakarta.faces.resource/**").permitAll()
                .requestMatchers("/error/**", "/pages/error/**").permitAll()
                .requestMatchers("/api/ping").permitAll()
                .requestMatchers("/api/context/**").permitAll()
                .requestMatchers("/api/ark:/**").permitAll()
                .requestMatchers("/api/recording-units/**").permitAll()
                .requestMatchers("/register/**").permitAll()
                .requestMatchers("/actuator/**").permitAll()

                .requestMatchers(EndpointRequest.to(HealthEndpoint.class)).permitAll()
                .anyRequest().authenticated()
        );
        http.formLogin(login -> login
                .loginPage(LOGIN)
                .loginProcessingUrl(LOGIN)
                .usernameParameter("email")
                .successHandler(loginSuccessHandler)
        );
        http.logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/")
        );

        http.addFilterBefore(userInfoContextFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Password encoder bean.
     * @return BCryptPasswordEncoder object to encode passwords with bcrypt.
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CSRF désactivé pour Swagger (accès anonyme) et l'API v1 (JWT Bearer, sans cookie de session).
     * L'application web JSF ({@link #webSecurityFilterChain}) conserve CSRF activé par défaut.
     */
    @SuppressWarnings("java:S4502")
    private static Customizer<CsrfConfigurer<HttpSecurity>> csrfDisabled() {
        return AbstractHttpConfigurer::disable;
    }

}

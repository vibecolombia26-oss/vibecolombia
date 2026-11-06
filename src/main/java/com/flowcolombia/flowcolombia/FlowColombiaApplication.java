package com.flowcolombia.flowcolombia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Punto de entrada de la tienda.
 *
 * <p>{@code @EnableAsync} habilita el envío en segundo plano de los eventos
 * de la API de Conversiones de Meta ({@link com.flowcolombia.flowcolombia.service.MetaConversionsService}),
 * de modo que el checkout nunca espera a Facebook.</p>
 */
@SpringBootApplication
@EnableAsync
public class FlowColombiaApplication {
	public static void main(String[] args) {
		SpringApplication.run(FlowColombiaApplication.class, args);
	}
}

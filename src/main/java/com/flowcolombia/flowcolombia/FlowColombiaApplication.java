package com.flowcolombia.flowcolombia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto de entrada de la tienda.
 *
 * <p>{@code @EnableAsync} habilita el envío en segundo plano de los eventos
 * de la API de Conversiones de Meta ({@link com.flowcolombia.flowcolombia.service.MetaConversionsService}),
 * de modo que el checkout nunca espera a Facebook.</p>
 *
 * <p>{@code @EnableScheduling} habilita el job de las 6 horas que sincroniza el
 * estado de los pedidos abiertos con Dropi
 * ({@link com.flowcolombia.flowcolombia.service.DropiSyncService}). Sin esta
 * anotación el {@code @Scheduled} no se ejecuta nunca.</p>
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class FlowColombiaApplication {
	public static void main(String[] args) {
		SpringApplication.run(FlowColombiaApplication.class, args);
	}
}

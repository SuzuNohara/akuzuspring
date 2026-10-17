package com.nexus.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Tareas periodicas (vencimientos y recordatorios de la planeacion en pareja). */
@Configuration
@EnableScheduling
public class SchedulingConfig {}

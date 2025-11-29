package com.nexus.service;

import com.nexus.dto.CreateEventRequest;
import com.nexus.dto.CreateEventResponse;
import com.nexus.dto.EventResponse;
import com.nexus.dto.ReminderDTO;
import com.nexus.dto.UpdateEventRequest;
import com.nexus.entity.Event;
import com.nexus.entity.EventException;
import com.nexus.entity.EventReminder;
import com.nexus.entity.EventStatus;
import com.nexus.entity.Link;
import com.nexus.entity.User;
import com.nexus.exception.BadRequestException;
import com.nexus.exception.ResourceNotFoundException;
import com.nexus.repository.EventExceptionRepository;
import com.nexus.repository.EventRepository;
import com.nexus.repository.LinkRepository;
import com.nexus.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {
    
    private final EventRepository eventRepository;
    private final LinkRepository linkRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final EventExceptionRepository eventExceptionRepository;
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Transactional
    public CreateEventResponse createEvent(Long userId, CreateEventRequest request) {
        log.info("Usuario {} creando evento: {}", userId, request.getTitle());
        
        // 1. Validar que el usuario existe y está activo
        User creator = userRepository.findActiveById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        
        // 2. Validar que el usuario tiene un vínculo activo (RN-15)
        Link activeLink = linkRepository.findActiveLinkByUserId(userId)
            .orElseThrow(() -> new BadRequestException("No tienes un vínculo activo. Debes estar conectado con tu pareja para crear eventos"));
        
        // 3. Validar datos del evento
        validateEventData(request);
        
        // 4. Convertir fechas de String ISO 8601 a Instant
        Instant startInstant = Instant.parse(request.getStartDateTime());
        Instant endInstant = Instant.parse(request.getEndDateTime());
        
        // 5. Crear el evento
        Event event = Event.builder()
            .title(request.getTitle())
            .startDateTime(startInstant)
            .endDateTime(endInstant)
            .location(request.getLocation())
            .category(request.getCategory())
            .description(request.getDescription())
            .creator(creator)
            .link(activeLink)
            .status(EventStatus.PENDING)
            .creatorApproved(true) // El creador aprueba automáticamente
            .partnerApproved(false) // Pendiente de aprobación de la pareja
            .isRecurring(request.getIsRecurring() != null ? request.getIsRecurring() : false)
            .recurrencePattern(request.getRecurrencePattern())
            .color(request.getColor())
            .reminderMinutes(request.getReminderMinutes())
            .build();
        
        // 5. Guardar el evento
        Event savedEvent = eventRepository.save(event);
        log.info("Evento {} creado exitosamente con ID: {}", savedEvent.getTitle(), savedEvent.getId());
        
        // 5.1. Crear recordatorios si existen
        if (request.getReminders() != null && !request.getReminders().isEmpty()) {
            List<EventReminder> reminders = new ArrayList<>();
            for (ReminderDTO reminderDTO : request.getReminders()) {
                EventReminder reminder = new EventReminder();
                reminder.setEvent(savedEvent);
                reminder.setMinutesBefore(reminderDTO.getMinutesBefore());
                reminder.setLabel(reminderDTO.getLabel());
                reminders.add(reminder);
            }
            savedEvent.setReminders(reminders);
            savedEvent = eventRepository.save(savedEvent);
        } else {
            savedEvent.setReminders(new ArrayList<>());
        }
        
        // 6. Obtener información de la pareja para notificación
        User partner = getPartnerUser(activeLink, userId);
        
        // 7. Enviar notificación a la pareja
        String notificationStatus = "Notificación pendiente";
        try {
            if (partner != null) {
                notificationService.sendEventApprovalNotification(
                    partner.getEmail(),
                    partner.getDisplayName() != null ? partner.getDisplayName() : partner.getNickname(),
                    creator.getDisplayName() != null ? creator.getDisplayName() : creator.getNickname(),
                    savedEvent.getTitle()
                );
                notificationStatus = "Notificación enviada exitosamente";
                log.info("Notificación de aprobación enviada a {}", partner.getEmail());
            }
        } catch (Exception e) {
            log.warn("Error enviando notificación de evento a la pareja: {}", e.getMessage());
            notificationStatus = "Error enviando notificación";
        }
        
        // 8. Crear respuesta
        EventResponse eventResponse = mapToEventResponse(savedEvent);
        
        String message = String.format("Evento '%s' creado exitosamente. Está pendiente de aprobación de %s",
            savedEvent.getTitle(),
            partner != null ? (partner.getDisplayName() != null ? partner.getDisplayName() : partner.getNickname()) : "tu pareja");
        
        return CreateEventResponse.builder()
            .success(true)
            .message(message)
            .event(eventResponse)
            .partnerNotificationStatus(notificationStatus)
            .build();
    }
    
    private void validateEventData(CreateEventRequest request) {
        // Parse fechas de String a Instant para validación
        Instant startInstant = Instant.parse(request.getStartDateTime());
        Instant endInstant = Instant.parse(request.getEndDateTime());
        
        // Validar que la fecha de inicio es antes que la fecha de fin
        if (!startInstant.isBefore(endInstant)) {
            throw new BadRequestException("La fecha de inicio debe ser anterior a la fecha de fin");
        }
        
        // Validar que las fechas son en el futuro
        Instant now = Instant.now();
        if (startInstant.isBefore(now)) {
            throw new BadRequestException("La fecha de inicio debe ser en el futuro");
        }
        
        // Validar duración razonable del evento (no más de 7 días)
        Instant sevenDaysLater = startInstant.plus(7, java.time.temporal.ChronoUnit.DAYS);
        if (sevenDaysLater.isBefore(endInstant)) {
            throw new BadRequestException("La duración del evento no puede exceder los 7 días");
        }
        
        // Validar recordatorio (si se proporciona)
        if (request.getReminderMinutes() != null) {
            if (request.getReminderMinutes() < 0 || request.getReminderMinutes() > 10080) { // Máximo 1 semana
                throw new BadRequestException("El recordatorio debe estar entre 0 y 10080 minutos (1 semana)");
            }
        }
    }
    
    private User getPartnerUser(Link link, Long userId) {
        if (link.getInitiatorUser().getId().equals(userId)) {
            return link.getPartnerUser();
        } else {
            return link.getInitiatorUser();
        }
    }
    
    public List<EventResponse> getUserEvents(Long userId) {
        log.info("Obteniendo eventos para usuario: {}", userId);
        
        // Verificar que el usuario existe
        userRepository.findActiveById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        
        List<Event> events = eventRepository.findByUserIdAndNotDeleted(userId);
        return events.stream()
            .map(this::mapToEventResponse)
            .collect(Collectors.toList());
    }
    
    public List<EventResponse> getPendingApprovalEvents(Long userId) {
        log.info("Obteniendo eventos pendientes de aprobación para usuario: {}", userId);
        
        List<Event> pendingEvents = eventRepository.findPendingApprovalByUserId(userId);
        return pendingEvents.stream()
            .map(this::mapToEventResponse)
            .collect(Collectors.toList());
    }
    
    public long countPendingApprovals(Long userId) {
        return eventRepository.countPendingApprovalByUserId(userId);
    }
    
    @Transactional
    public EventResponse approveEvent(Long userId, Long eventId) {
        log.info("Usuario {} aprobando evento {}", userId, eventId);
        
        // Verificar que el evento existe y el usuario tiene acceso
        Event event = eventRepository.findByIdAndUserId(eventId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Evento no encontrado"));
        
        // Verificar que el usuario no es el creador
        if (event.getCreator().getId().equals(userId)) {
            throw new BadRequestException("No puedes aprobar tu propio evento");
        }
        
        // Verificar que el evento está pendiente
        if (event.getPartnerApproved()) {
            throw new BadRequestException("Este evento ya ha sido aprobado");
        }
        
        // Aprobar el evento
        event.setPartnerApproved(true);
        event.setPartnerApprovedAt(Instant.now());
        
        // Actualizar status: si ambos aprobaron, CONFIRMED, sino PENDING
        if (event.isFullyApproved()) {
            event.setStatus(EventStatus.CONFIRMED);
        } else {
            event.setStatus(EventStatus.PENDING);
        }
        
        Event savedEvent = eventRepository.save(event);
        
        log.info("Evento {} aprobado por usuario {}", eventId, userId);
        
        // Enviar notificación al creador
        try {
            User creator = savedEvent.getCreator();
            notificationService.sendEventConfirmedNotification(
                creator.getEmail(),
                creator.getDisplayName() != null ? creator.getDisplayName() : creator.getNickname(),
                savedEvent.getTitle()
            );
        } catch (Exception e) {
            log.warn("Error enviando notificación de confirmación: {}", e.getMessage());
        }
        
        return mapToEventResponse(savedEvent);
    }
    
    private EventResponse mapToEventResponse(Event event) {
        User partner = getPartnerUser(event.getLink(), event.getCreator().getId());
        
        // Obtener las fechas de excepción si el evento es recurrente
        List<Instant> exceptionDates = new java.util.ArrayList<>();
        if (Boolean.TRUE.equals(event.getIsRecurring())) {
            try {
                exceptionDates = eventExceptionRepository.findExceptionDatesByEventId(event.getId());
            } catch (Exception e) {
                log.warn("No se pudieron obtener excepciones para evento {}: {}", event.getId(), e.getMessage());
            }
        }
        
        return EventResponse.builder()
            .id(event.getId())
            .title(event.getTitle())
            .startDateTime(event.getStartDateTime())
            .endDateTime(event.getEndDateTime())
            .location(event.getLocation())
            .category(event.getCategory())
            .description(event.getDescription())
            .status(event.getStatus())
            .creatorUserId(event.getCreator().getId())
            .creatorName(event.getCreator().getDisplayName())
            .creatorNickname(event.getCreator().getNickname())
            .linkId(event.getLink().getId())
            .partnerName(partner != null ? partner.getDisplayName() : null)
            .partnerNickname(partner != null ? partner.getNickname() : null)
            .creatorApproved(event.getCreatorApproved())
            .partnerApproved(event.getPartnerApproved())
            .fullyApproved(event.isFullyApproved())
            .creatorApprovedAt(event.getCreatorApprovedAt())
            .partnerApprovedAt(event.getPartnerApprovedAt())
            .isRecurring(event.getIsRecurring())
            .recurrencePattern(event.getRecurrencePattern())
            .color(event.getColor())
            .reminderMinutes(event.getReminderMinutes())
            .reminders(event.getReminders() != null ? event.getReminders().stream()
                .map(r -> new ReminderDTO(r.getMinutesBefore(), r.getLabel()))
                .collect(java.util.stream.Collectors.toList()) : new java.util.ArrayList<>())
            .exceptionDates(exceptionDates)
            .createdAt(event.getCreatedAt())
            .updatedAt(event.getUpdatedAt())
            .pendingApprovalUserId(event.getPendingApprovalUserId())
            .pendingApprovalUserName(partner != null && event.getStatus() == EventStatus.PENDING && !event.getPartnerApproved() ? 
                (partner.getDisplayName() != null ? partner.getDisplayName() : partner.getNickname()) : null)
            .build();
    }
    
    /**
     * Actualizar un evento existente (CU17)
     * Implementa RN-17: Aprobación mutua para modificaciones
     */
    @Transactional
    public EventResponse updateEvent(Long eventId, Long userId, UpdateEventRequest request) {
        log.info("Actualizando evento {} por usuario {}", eventId, userId);
        
        // 1. Buscar el evento
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado"));
        
        // 2. Verificar que el usuario tenga permiso (debe ser creador o partner)
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        
        Link link = event.getLink();
        boolean isCreator = event.getCreator().getId().equals(userId);
        boolean isPartner = link.getInitiatorUser().getId().equals(userId) || 
                           link.getPartnerUser().getId().equals(userId);
        
        if (!isPartner) {
            throw new IllegalArgumentException("No tienes permiso para editar este evento");
        }
        
        // Determinar si se están modificando campos que requieren re-aprobación
        boolean requiresReapproval = false;
        
        // 3. Actualizar campos y detectar cambios importantes
        if (request.getTitle() != null) {
            event.setTitle(request.getTitle());
            requiresReapproval = true; // Título cambia el contenido del evento
        }
        if (request.getStartDateTime() != null) {
            // Parse ISO 8601 with timezone to Instant (UTC)
            Instant startInstant = Instant.parse(request.getStartDateTime());
            event.setStartDateTime(startInstant);
            requiresReapproval = true; // Fecha/hora requiere re-aprobación
        }
        if (request.getEndDateTime() != null) {
            Instant endInstant = Instant.parse(request.getEndDateTime());
            event.setEndDateTime(endInstant);
            requiresReapproval = true; // Fecha/hora requiere re-aprobación
        }
        if (request.getLocation() != null) {
            event.setLocation(request.getLocation());
            requiresReapproval = true; // Ubicación requiere re-aprobación
        }
        if (request.getCategory() != null) event.setCategory(request.getCategory());
        if (request.getDescription() != null) event.setDescription(request.getDescription());
        if (request.getIsRecurring() != null) {
            event.setIsRecurring(request.getIsRecurring());
            requiresReapproval = true; // Recurrencia requiere re-aprobación
        }
        if (request.getRecurrencePattern() != null) {
            event.setRecurrencePattern(request.getRecurrencePattern());
            requiresReapproval = true; // Patrón de recurrencia requiere re-aprobación
        }
        if (request.getColor() != null) event.setColor(request.getColor());
        if (request.getReminderMinutes() != null) event.setReminderMinutes(request.getReminderMinutes());
        
        // 4. Actualizar recordatorios (NO requiere re-aprobación)
        if (request.getReminders() != null) {
            event.getReminders().clear();
            for (ReminderDTO dto : request.getReminders()) {
                EventReminder reminder = EventReminder.builder()
                    .event(event)
                    .minutesBefore(dto.getMinutesBefore())
                    .label(dto.getLabel())
                    .build();
                event.getReminders().add(reminder);
            }
        }
        
        // 5. RN-17: Resetear aprobaciones SOLO si se modificaron campos críticos
        if (requiresReapproval) {
            if (isCreator) {
                // El creador mantiene su aprobación, resetear la de la pareja
                event.setCreatorApproved(true);
                event.setPartnerApproved(false);
                event.setPartnerApprovedAt(null);
            } else {
                // La pareja editó, mantiene su aprobación, resetear la del creador
                event.setPartnerApproved(true);
                event.setCreatorApproved(false);
                event.setCreatorApprovedAt(null);
            }
            
            event.setStatus(EventStatus.PENDING);
        }
        // Si solo se editaron recordatorios, color, categoría o descripción, no cambiar el estado
        
        // 6. Guardar
        Event updatedEvent = eventRepository.save(event);
        log.info("Evento {} actualizado exitosamente", eventId);
        
        // 7. Notificar a la otra persona SOLO si requiere re-aprobación
        if (requiresReapproval) {
            User otherUser = isCreator ? 
                getPartnerUser(link, userId) : 
                event.getCreator();
            
            try {
                if (otherUser != null) {
                    notificationService.sendEventApprovalNotification(
                        otherUser.getEmail(),
                        otherUser.getDisplayName() != null ? otherUser.getDisplayName() : otherUser.getNickname(),
                        user.getDisplayName() != null ? user.getDisplayName() : user.getNickname(),
                        updatedEvent.getTitle()
                    );
                    log.info("Notificación de edición enviada a {}", otherUser.getEmail());
                }
            } catch (Exception e) {
                log.warn("Error enviando notificación de edición: {}", e.getMessage());
            }
        } else {
            log.info("Edición de campos no críticos (recordatorios, color, etc.) - No se requiere re-aprobación");
        }
        
        return mapToEventResponse(updatedEvent);
    }
    
    /**
     * Rechazar un evento (usuario rechaza invitación a evento)
     * Usa transacción independiente (REQUIRES_NEW) para commit inmediato
     * y evitar que OSIV re-sincronice el estado cached
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EventResponse rejectEvent(Long userId, Long eventId) {
        log.info("Usuario {} rechazando evento {}", userId, eventId);
        
        // 1. Verificar que el evento existe y el usuario tiene acceso
        Event event = eventRepository.findByIdAndUserId(eventId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Evento no encontrado"));
        
        // 2. Verificar que el usuario no es el creador
        if (event.getCreator().getId().equals(userId)) {
            throw new BadRequestException("No puedes rechazar tu propio evento");
        }
        
        // 3. Verificar que el evento no está ya rechazado
        if (event.getStatus() == EventStatus.REJECTED) {
            throw new BadRequestException("Este evento ya ha sido rechazado");
        }
        
        // 4. Guardar información del creador para notificación (antes de limpiar caché)
        User creator = event.getCreator();
        String eventTitle = event.getTitle();
        String creatorEmail = creator.getEmail();
        String creatorDisplayName = creator.getDisplayName() != null ? creator.getDisplayName() : creator.getNickname();
        
        // 5. Ejecutar UPDATE nativo SQL (bypass completo de JPA/Hibernate)
        log.info("Ejecutando UPDATE nativo para cambiar status a REJECTED");
        int rowsUpdated = eventRepository.updateEventStatus(eventId);
        
        if (rowsUpdated == 0) {
            throw new RuntimeException("No se pudo actualizar el evento");
        }
        
        log.info("UPDATE ejecutado: {} filas actualizadas", rowsUpdated);
        
        // 6. CRÍTICO: Limpiar el caché de primer nivel de Hibernate
        // Esto fuerza que cualquier find() posterior vaya a la base de datos
        entityManager.clear();
        log.info("Caché de EntityManager limpiado");
        
        // 7. Recargar la entidad desde la base de datos (post-UPDATE)
        event = entityManager.find(Event.class, eventId);
        
        if (event == null) {
            throw new ResourceNotFoundException("Evento no encontrado después de actualizar");
        }
        
        log.info("Evento {} rechazado exitosamente - Status: {}", eventId, event.getStatus());
        
        // 8. Enviar notificación al creador
        try {
            notificationService.sendEventRejectedNotification(
                creatorEmail,
                creatorDisplayName,
                eventTitle
            );
            log.info("Notificación de rechazo enviada a {}", creatorEmail);
        } catch (Exception e) {
            log.warn("Error enviando notificación de rechazo: {}", e.getMessage());
        }
        
        // 9. Retornar el evento con el status correcto (REJECTED)
        return mapToEventResponse(event);
    }
    
    /**
     * CU19 - Eliminar evento
     * Permite que un usuario vinculado elimine un evento del calendario compartido
     */
    @Transactional
    public void deleteEvent(Long eventId, Long userId) {
        log.info("Usuario {} eliminando evento {}", userId, eventId);
        
        // 1. Verificar que el usuario existe
        User user = userRepository.findActiveById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        
        // 2. Verificar que el evento existe
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("Evento no encontrado"));
        
        // 3. Verificar que tiene un vínculo activo (RN-15)
        Link activeLink = linkRepository.findActiveLinkByUserId(userId)
            .orElseThrow(() -> new BadRequestException("No tienes un vínculo activo"));
        
        // 4. Verificar que el usuario tiene permiso (debe ser parte del vínculo del evento)
        if (!event.getLink().getId().equals(activeLink.getId())) {
            throw new BadRequestException("No tienes permiso para eliminar este evento");
        }
        
        // 5. Eliminar el evento (cascada eliminará recordatorios)
        User partner = getPartnerUser(activeLink, userId);
        String deletedBy = user.getDisplayName() != null ? user.getDisplayName() : user.getNickname();
        
        eventRepository.delete(event);
        log.info("Evento {} eliminado exitosamente por usuario {}", eventId, userId);
        
        // 6. Notificar a la pareja
        try {
            if (partner != null) {
                notificationService.sendEventDeletedNotification(
                    partner.getEmail(),
                    partner.getDisplayName() != null ? partner.getDisplayName() : partner.getNickname(),
                    deletedBy,
                    event.getTitle()
                );
                log.info("Notificación de eliminación enviada a {}", partner.getEmail());
            }
        } catch (Exception e) {
            log.warn("Error enviando notificación de eliminación: {}", e.getMessage());
        }
    }
    
    /**
     * Agregar una excepción a un evento recurrente (eliminar instancia específica)
     */
    @Transactional
    public void addEventException(Long eventId, String exceptionDateStr, Long userId) {
        log.info("Agregando excepción al evento {} para la fecha {} por usuario {}", eventId, exceptionDateStr, userId);
        
        // 1. Buscar el evento
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new IllegalArgumentException("Evento no encontrado"));
        
        // 2. Verificar que el usuario tenga permiso
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));
        
        Link link = event.getLink();
        boolean isCreator = event.getCreator().getId().equals(userId);
        boolean isPartner = (link.getInitiatorUser().getId().equals(userId) || link.getPartnerUser().getId().equals(userId)) && !isCreator;
        
        if (!isCreator && !isPartner) {
            throw new IllegalArgumentException("No tienes permiso para modificar este evento");
        }
        
        // 3. Verificar que el evento sea recurrente
        if (!Boolean.TRUE.equals(event.getIsRecurring())) {
            throw new IllegalArgumentException("Solo se pueden agregar excepciones a eventos recurrentes");
        }
        
        // 4. Parsear la fecha de excepción
        Instant exceptionDate;
        try {
            exceptionDate = Instant.parse(exceptionDateStr);
        } catch (Exception e) {
            throw new IllegalArgumentException("Formato de fecha inválido. Use ISO 8601 con zona horaria");
        }
        
        // 5. Verificar que no exista ya esta excepción
        if (eventExceptionRepository.existsByEventIdAndExceptionDate(eventId, exceptionDate)) {
            throw new IllegalArgumentException("Ya existe una excepción para esta fecha");
        }
        
        // 6. Crear la excepción
        EventException exception = EventException.builder()
            .event(event)
            .exceptionDate(exceptionDate)
            .exceptionType(EventException.ExceptionType.DELETED)
            .build();
        
        eventExceptionRepository.save(exception);
        log.info("Excepción agregada exitosamente al evento {}", eventId);
    }
}

package com.nexus.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "event_reminders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventReminder {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;
    
    @Column(name = "minutes_before", nullable = false)
    private Integer minutesBefore;
    
    @Column(name = "label", length = 100)
    private String label;
}

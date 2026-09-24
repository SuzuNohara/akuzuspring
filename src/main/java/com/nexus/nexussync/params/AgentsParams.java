package com.nexus.nexussync.params;

/**
 * Model and timeout of each arkannie agent (unit U5).
 *
 * @param personaModel model of the persona agent
 * @param personaTimeout timeout of the persona agent in seconds
 * @param mediatorModel model of the mediator agent
 * @param mediatorTimeout timeout of the mediator agent in seconds
 * @param mediatorClimate how much emotional climate the mediator receives
 */
public record AgentsParams(
    String personaModel,
    int personaTimeout,
    String mediatorModel,
    int mediatorTimeout,
    MediatorClimate mediatorClimate) {}

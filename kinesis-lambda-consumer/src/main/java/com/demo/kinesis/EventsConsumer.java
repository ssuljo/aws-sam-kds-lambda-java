package com.demo.kinesis;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.KinesisEvent;
import com.demo.kinesis.model.CartAbandonmentEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;


public class EventsConsumer implements RequestHandler<KinesisEvent, Void> {

    private final ObjectMapper objMapper = new ObjectMapper();
    private EventsManager eventsMgr = new EventsManager();
    static final long CART_ABANDONMENT_TIME_MINS = 30;

    /**
     * Entry point for the Lambda function. Handles incoming Kinesis events.
     *
     * @param event   The KinesisEvent containing one or more KinesisEventRecords.
     * @param context The Lambda execution context.
     * @return null (Void) as the Lambda function does not produce a direct result.
     */
    @Override
    public Void handleRequest(KinesisEvent event, Context context) {
        LambdaLogger logger = context.getLogger();

        logger.log("Kinesis Java Lambda Consumer Invoked: records = " + event.getRecords().size());

        // Process each Kinesis event record
        for (KinesisEvent.KinesisEventRecord rec : event.getRecords()) processRecord(rec, logger);

        // Analyze the events and calculate the average potential order size
        analyzeEvents(logger);

        // Reset the EventsManager for the next reporting interval
        resetEvents();

        return null;
    }

    /**
     * Processes a single KinesisEventRecord, attempting to deserialize it into a `CartAbandonmentEvent`
     * and adds it to the `EventsManager` for further processing.
     *
     * @param record The KinesisClientRecord to be processed.
     * @param logger The LambdaLogger for logging messages.
     */
    private void processRecord(KinesisEvent.KinesisEventRecord record, LambdaLogger logger) {
        byte[] arr = record.getKinesis().getData().array();
        try {
            CartAbandonmentEvent event = objMapper.readValue(arr, CartAbandonmentEvent.class);
            eventsMgr.addEvent(event);
            logger.log("Processed cart event: " + event);
        } catch (IOException e) {
            logger.log(String.format("Failed to de-serialize record. Record Data: %s",
                    new String(arr, StandardCharsets.UTF_8)));
            logger.log("Error: " + e.getMessage());

        }
    }

    /**
     * Analyzes events in the `EventsManager` to calculate the average potential order size and logs the result.
     *
     * @param logger The LambdaLogger for logging messages.
     */
    private void analyzeEvents(LambdaLogger logger) {
        double avgPotentialOrderSize = eventsMgr
                .calculateAvgPotentialOrderSize(System.currentTimeMillis(), CART_ABANDONMENT_TIME_MINS);
        logger.log(String.format("Average Potential Order $%.2f", avgPotentialOrderSize));
    }

    /**
     * Resets the `EventsManager` to prepare for the next reporting interval.
     */
    private void resetEvents() {
        eventsMgr = new EventsManager();
    }

}

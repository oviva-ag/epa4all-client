package com.oviva.telematik.epa4all.restservice.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.spi.ContextAwareBase;
import com.google.auto.service.AutoService;
import com.oviva.telematik.epa4all.restservice.Main;
import java.util.Optional;
import org.slf4j.Logger;

@AutoService(Configurator.class)
public class LogConfigurator extends ContextAwareBase implements Configurator {
  @Override
  public ExecutionStatus configure(LoggerContext context) {
    addInfo("Setting up default configuration.");

    var ca = new ConsoleAppender<ILoggingEvent>();
    ca.setContext(context);
    ca.setName("console");

    var encoder = new JsonEncoder();
    encoder.setContext(context);

    ca.setEncoder(encoder);
    ca.start();

    var rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
    rootLogger.addAppender(ca);

    getLevel().ifPresent(rootLogger::setLevel);

    return ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY;
  }

  private Optional<Level> getLevel() {
    return Optional.ofNullable(System.getenv(Main.CONFIG_PREFIX + "_LOG_LEVEL"))
        .map(Level::valueOf);
  }
}

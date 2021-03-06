package org.whispersystems.textsecuregcm.storage;

import com.google.protobuf.ByteString;
import org.skife.jdbi.v2.SQLStatement;
import org.skife.jdbi.v2.StatementContext;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.Binder;
import org.skife.jdbi.v2.sqlobject.BinderFactory;
import org.skife.jdbi.v2.sqlobject.BindingAnnotation;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.Mapper;
import org.skife.jdbi.v2.tweak.ResultSetMapper;
import org.whispersystems.textsecuregcm.entities.MessageProtos.OutgoingMessageSignal;
import org.whispersystems.textsecuregcm.util.Pair;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public abstract class Messages {

  private static final String ID                 = "id";
  private static final String TYPE               = "type";
  private static final String RELAY              = "relay";
  private static final String TIMESTAMP          = "timestamp";
  private static final String SOURCE             = "source";
  private static final String SOURCE_DEVICE      = "source_device";
  private static final String DESTINATION        = "destination";
  private static final String DESTINATION_DEVICE = "destination_device";
  private static final String MESSAGE            = "message";

  @SqlQuery("INSERT INTO messages (" + TYPE + ", " + RELAY + ", " + TIMESTAMP + ", " + SOURCE + ", " + SOURCE_DEVICE + ", " + DESTINATION + ", " + DESTINATION_DEVICE + ", " + MESSAGE + ") " +
            "VALUES (:type, :relay, :timestamp, :source, :source_device, :destination, :destination_device, :message) " +
            "RETURNING (SELECT COUNT(id) FROM messages WHERE " + DESTINATION + " = :destination AND " + DESTINATION_DEVICE + " = :destination_device AND " + TYPE + " != " + OutgoingMessageSignal.Type.RECEIPT_VALUE + ")")
  abstract int store(@MessageBinder OutgoingMessageSignal message,
                     @Bind("destination") String destination,
                     @Bind("destination_device") long destinationDevice);

  @Mapper(MessageMapper.class)
  @SqlQuery("SELECT * FROM messages WHERE " + DESTINATION + " = :destination AND " + DESTINATION_DEVICE + " = :destination_device")
  abstract List<Pair<Long, OutgoingMessageSignal>> load(@Bind("destination") String destination,
                                                        @Bind("destination_device") long destinationDevice);

  @SqlUpdate("DELETE FROM messages WHERE " + ID + " = :id")
  abstract void remove(@Bind("id") long id);

  @SqlUpdate("DELETE FROM messages WHERE " + DESTINATION + " = :destination")
  abstract void clear(@Bind("destination") String destination);

  @SqlUpdate("VACUUM messages")
  public abstract void vacuum();

  public static class MessageMapper implements ResultSetMapper<Pair<Long, OutgoingMessageSignal>> {
    @Override
    public Pair<Long, OutgoingMessageSignal> map(int i, ResultSet resultSet, StatementContext statementContext)
        throws SQLException
    {
      return new Pair<>(resultSet.getLong(ID),
                        OutgoingMessageSignal.newBuilder()
                                             .setType(resultSet.getInt(TYPE))
                                             .setRelay(resultSet.getString(RELAY))
                                             .setTimestamp(resultSet.getLong(TIMESTAMP))
                                             .setSource(resultSet.getString(SOURCE))
                                             .setSourceDevice(resultSet.getInt(SOURCE_DEVICE))
                                             .setMessage(ByteString.copyFrom(resultSet.getBytes(MESSAGE)))
                                             .build());
    }
  }

  @BindingAnnotation(MessageBinder.AccountBinderFactory.class)
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.PARAMETER})
  public @interface MessageBinder {
    public static class AccountBinderFactory implements BinderFactory {
      @Override
      public Binder build(Annotation annotation) {
        return new Binder<MessageBinder, OutgoingMessageSignal>() {
          @Override
          public void bind(SQLStatement<?> sql,
                           MessageBinder accountBinder,
                           OutgoingMessageSignal message)
          {
            sql.bind(TYPE, message.getType());
            sql.bind(RELAY, message.getRelay());
            sql.bind(TIMESTAMP, message.getTimestamp());
            sql.bind(SOURCE, message.getSource());
            sql.bind(SOURCE_DEVICE, message.getSourceDevice());
            sql.bind(MESSAGE, message.getMessage().toByteArray());
          }
        };
      }
    }
  }


}
